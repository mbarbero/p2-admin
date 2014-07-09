/*******************************************************************************
 * Copyright (c) 2014 Obeo.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipselabs.equinox.p2.moreapp;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.URIUtil;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.internal.repository.tools.CompositeRepositoryApplication;
import org.eclipse.equinox.p2.internal.repository.tools.Messages;
import org.eclipse.equinox.p2.internal.repository.tools.RepositoryDescriptor;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.repository.ICompositeRepository;
import org.eclipse.equinox.p2.repository.IRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.eclipse.osgi.util.NLS;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

/**
 * @author <a href="mailto:mikael.barbero@obeo.fr">Mikael Barbero</a>
 *
 */
@SuppressWarnings("restriction")
public class CompositeRepository extends CompositeRepositoryApplication implements IApplication {

	private static Integer EXIT_ERR__COMMAND_LINE_ARGS = Integer.valueOf(255);
	private static Integer EXIT_ERR__RUN = Integer.valueOf(256);
	private boolean list;
	private List<RepositoryDescriptor> destinationRepos = Lists.newArrayList();
	
	/** 
	 * {@inheritDoc}
	 * @see org.eclipse.equinox.app.IApplication#start(org.eclipse.equinox.app.IApplicationContext)
	 */
	@Override
	public Object start(IApplicationContext context) throws Exception {
		String[] args = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		try {
			processCommandLineArgs(args);
		} catch (Exception e) {
			System.err.println(e.getMessage());
			usage();
			return EXIT_ERR__COMMAND_LINE_ARGS;
		}
		// perform the transformation
		try {
			if (list) {
				initRepositoriesForList();
				ICompositeRepository<?> metadataRepo = (ICompositeRepository<?>) destinationMetadataRepository;
				for (URI uri : metadataRepo.getChildren()) {
					System.out.println(uri.toString());
				}
			} else {
				run(null);
			}
		} catch (Exception e) {
			System.err.println(e.getMessage());
			usage();
			return EXIT_ERR__RUN;
		}
		return IApplication.EXIT_OK;
	}

	private void initRepositoriesForList() throws ProvisionException {
		IArtifactRepositoryManager artifactRepositoryManager = getArtifactRepositoryManager();
		IMetadataRepositoryManager metadataRepositoryManager = getMetadataRepositoryManager();
		
		RepositoryDescriptor artifactRepoDescriptor = null;
		RepositoryDescriptor metadataRepoDescriptor = null;

		Iterator<RepositoryDescriptor> iter = destinationRepos.iterator();
		while (iter.hasNext() && (artifactRepoDescriptor == null || metadataRepoDescriptor == null)) {
			RepositoryDescriptor repo = iter.next();
			if (repo.isArtifact() && artifactRepoDescriptor == null)
				artifactRepoDescriptor = repo;
			if (repo.isMetadata() && metadataRepoDescriptor == null)
				metadataRepoDescriptor = repo;
		}

		if (artifactRepoDescriptor != null)
			artifactRepositoryManager.removeRepository(artifactRepoDescriptor.getRepoLocation());

			// first try and load to see if one already exists at that location.
			try {
				destinationArtifactRepository = artifactRepositoryManager.loadRepository(artifactRepoDescriptor.getRepoLocation(), null);
			} catch (ProvisionException e) {
				// re-throw the exception if we got anything other than "repo not found"
				if (e.getStatus().getCode() != ProvisionException.REPOSITORY_NOT_FOUND) {
					if (e.getCause() instanceof MalformedURLException)
						throw new ProvisionException(NLS.bind(Messages.exception_invalidDestination, artifactRepoDescriptor.getRepoLocation()), e.getCause());
					throw e;
				}
			}
		if (metadataRepoDescriptor != null) {
			metadataRepositoryManager.removeRepository(metadataRepoDescriptor.getRepoLocation());

			// first try and load to see if one already exists at that location.
			try {
				destinationMetadataRepository = metadataRepositoryManager.loadRepository(metadataRepoDescriptor.getRepoLocation(), null);
			} catch (ProvisionException e) {
				// re-throw the exception if we got anything other than "repo not found"
				if (e.getStatus().getCode() != ProvisionException.REPOSITORY_NOT_FOUND) {
					if (e.getCause() instanceof MalformedURLException)
						throw new ProvisionException(NLS.bind(Messages.exception_invalidDestination, metadataRepoDescriptor.getRepoLocation()), e.getCause());
					throw e;
				}
			}
		}

		if (destinationMetadataRepository == null && destinationArtifactRepository == null)
			throw new ProvisionException(Messages.AbstractApplication_no_valid_destinations);
	}

	/**
	 * 
	 */
	private void usage() {
		System.err.println("Usage: -location repositoryURI [-list] [-add repository-list] [-remove repository-list] [-repositoryName name] [-validate] [-failOnExists] [-compressed]");
		System.err.println("  -location        URI of composite repository to create / modify");
		System.err.println("  -list            Whether the list of children should be printed on the standard output");
		System.err.println("  -add             Comma separated list of repositories URI to add to the composite");
		System.err.println("  -remove          Comma separated list of repositories URI to remove from the composite");
		System.err.println("  -repositoryName  The name of the composite as it should appears to client");
		System.err.println("  -validate        Child repositories claiming to contain the same artifact are compared using the given comparator. Either 'org.eclipse.equinox.p2.repository.tools.jar.comparator' or 'org.eclipse.equinox.artifact.md5.comparator'");
		System.err.println("  -failOnExists    Whether we should fail if the repository already exists. (Default is false)");
		System.err.println("  -compressed      Whether the composite repository should compressed. (Default is false)");
	}

	/*
	 * Iterate over the command-line arguments and prepare the transformer for processing.
	 */
	private void processCommandLineArgs(String[] args) throws Exception {
		if (args == null)
			throw new Exception("No argument provided");
		boolean compressed = false;
		String name = null;
		RepositoryDescriptor destination = null;
		for (int i = 0; i < args.length; i++) {
			String option = args[i];
			String arg = "";
			if (i == args.length - 1 || args[i + 1].startsWith("-")) {//$NON-NLS-1$
				// do nothgin
			} else {
				arg = args[++i];
			}

			if (option.equalsIgnoreCase("-location")) { //$NON-NLS-1$
				destination = new RepositoryDescriptor();
				destination.setLocation(URIUtil.fromString(arg));
				addDestination(destination);
			}
			
			if (option.equalsIgnoreCase("-add")) { //$NON-NLS-1$
				Iterable<String> repos = Splitter.on(',').trimResults().omitEmptyStrings().split(arg);
				for (String repo : repos) {
					RepositoryDescriptor child = new RepositoryDescriptor();
					child.setLocation(URIUtil.fromString(repo));
					addChild(child);
				}
			}
			
			if (option.equalsIgnoreCase("-remove")) {
				Iterable<String> repos = Splitter.on(',').trimResults().omitEmptyStrings().split(arg);
				for (String repo : repos) {
					RepositoryDescriptor child = new RepositoryDescriptor();
					child.setLocation(URIUtil.fromString(repo));
					removeChild(child);
				}
			}

			if (option.equalsIgnoreCase("-validate")) { //$NON-NLS-1$
				setComparator(arg);
			}

			if (option.equalsIgnoreCase("-failOnExists")) { //$NON-NLS-1$
				setFailOnExists(true);
			}
			
			if (option.equalsIgnoreCase("-compressed")) { //$NON-NLS-1$
				compressed = true;
			}
			
			if (option.equalsIgnoreCase("-repositoryName")) { //$NON-NLS-1$
				name = arg;
			}
			
			if (option.equalsIgnoreCase("-list")) {
				list = true;
			}
		}
		
		if (destination != null) {
			destination.setCompressed(compressed);
			if (name != null) {
				destination.setName(name);
			}
		}
	}
	
	protected boolean initDestinationRepository(IRepository<?> repository, RepositoryDescriptor descriptor) {
		if (super.initDestinationRepository(repository, descriptor)) {
			if (descriptor.isCompressed())
				repository.setProperty(IRepository.PROP_COMPRESSED, Boolean.toString(true));
			return true;
		}
		return false;
	}

	@Override
	public void addDestination(RepositoryDescriptor descriptor) {
		super.addDestination(descriptor);
		destinationRepos.add(descriptor);
	}
	
	/** 
	 * {@inheritDoc}
	 * @see org.eclipse.equinox.app.IApplication#stop()
	 */
	@Override
	public void stop() {
	}

}
