/**********************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.pde.internal.build.site;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.VersionConstraint;
import org.eclipse.pde.internal.build.*;
import org.eclipse.pde.internal.build.IPDEBuildConstants;
import org.eclipse.pde.internal.build.IXMLConstants;
import org.eclipse.update.core.*;

/**
 * This site represent a site at build time. A build time site is made of code
 * to compile, and a potential installation of eclipse (or derived products)
 * against which the code must be compiled.
 * Moreover this site provide access to a pluginRegistry.
 */
public class BuildTimeSite extends Site implements ISite, IPDEBuildConstants, IXMLConstants {
	private PDEState state;
	private boolean compile21 = AbstractScriptGenerator.isBuildingOSGi();
	
	public PDEState getRegistry() throws CoreException {
		if (state == null) {
			// create the registry according to the site where the code to compile is, and a existing installation of eclipse 
			BuildTimeSiteContentProvider contentProvider = (BuildTimeSiteContentProvider) getSiteContentProvider();
			state = new PDEState();
			
			if (compile21)
				new PluginRegistryConverter(contentProvider.getPluginPaths()).addRegistryToState(state);
			else 
				state.addBundles(contentProvider.getPluginPaths());
			
			state.resolveState();
			BundleDescription[] allBundles = state.getState().getBundles();
			BundleDescription[] resolvedBundles = state.getState().getResolvedBundles();
			if (allBundles.length == resolvedBundles.length)
				return state;
			
			//display a report of the unresolved constraints
			//TODO Need to connect that with the debug option of ant and some PDE-build debug options
			for (int i = 0; i < allBundles.length; i++) {
				if (! allBundles[i].isResolved()) {
					System.out.println(">>>" + allBundles[i].getUniqueId());
					VersionConstraint[] unsatisfiedConstraint = allBundles[i].getUnsatisfiedConstraints();
					for (int j = 0; j < unsatisfiedConstraint.length; j++) {
						System.out.println(unsatisfiedConstraint[j].getName());
					}	
				}
			}
		}
		return state;
	}

	public IFeature findFeature(String featureId) throws CoreException {
		ISiteFeatureReference[] features = getFeatureReferences();
		for (int i = 0; i < features.length; i++) {
			if (features[i].getVersionedIdentifier().getIdentifier().equals(featureId))
				return features[i].getFeature(null);
		}
		return null;
	}

}
