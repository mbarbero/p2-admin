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

import org.eclipse.core.runtime.URIUtil;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.equinox.p2.internal.repository.tools.CompositeRepositoryApplication;
import org.eclipse.equinox.p2.internal.repository.tools.RepositoryDescriptor;
import org.eclipse.equinox.p2.repository.IRepository;

import com.google.common.base.Splitter;

/**
 * @author <a href="mailto:mikael.barbero@obeo.fr">Mikael Barbero</a>
 *
 */
@SuppressWarnings("restriction")
public class CompositeRepository extends CompositeRepositoryApplication implements IApplication {

	private static Integer EXIT_ERR__COMMAND_LINE_ARGS = Integer.valueOf(255);
	private static Integer EXIT_ERR__RUN = Integer.valueOf(256);
	
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
			run(null);
		} catch (Exception e) {
			System.err.println(e.getMessage());
			usage();
			return EXIT_ERR__RUN;
		}
		return IApplication.EXIT_OK;
	}

	/**
	 * 
	 */
	private void usage() {
		System.err.println("Usage: -location repositoryURI [-add repository-list] [-remove repository-list] [-repositoryName name] [-validate] [-failOnExists] [-compressed]");
		System.err.println("  -location        URI of composite repository to create / modify");
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

	
	/** 
	 * {@inheritDoc}
	 * @see org.eclipse.equinox.app.IApplication#stop()
	 */
	@Override
	public void stop() {
	}

}
