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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.osgi.service.resolver.*;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.VersionConstraint;
import org.eclipse.pde.internal.build.*;
import org.eclipse.update.core.*;

/**
 * This site represent a site at build time. A build time site is made of code
 * to compile, and a potential installation of eclipse (or derived products)
 * against which the code must be compiled.
 * Moreover this site provide access to a pluginRegistry.
 */
public class BuildTimeSite extends Site implements ISite, IPDEBuildConstants, IXMLConstants {
	private PDEState state;
	private boolean compile21 = false;
	
	public PDEState getRegistry() throws CoreException {
		if (state == null) {
			// create the registry according to the site where the code to compile is, and a existing installation of eclipse 
			BuildTimeSiteContentProvider contentProvider = (BuildTimeSiteContentProvider) getSiteContentProvider();
			MultiStatus problems = new MultiStatus(PI_PDEBUILD, EXCEPTION_MODEL_PARSE, Policy.bind("exception.pluginParse"), null); //$NON-NLS-1$
			state = new PDEState();
			
			if (compile21)
				new PluginRegistryConverter(contentProvider.getPluginPaths()).addRegistryToState(state);
			else 
				state.addBundles(contentProvider.getPluginPaths());
			
			setExtraPrerequisites();
			state.resolveState();
			BundleDescription[] allBundles = state.getState().getBundles();
			BundleDescription[] resolvedBundles = state.getState().getResolvedBundles();
			if (allBundles.length == resolvedBundles.length)
				return state;
			
			//display a report of the unresolved constraints
			//TODO Need to connect that with the debug option of ant and some PDE-build debug options
			for (int i = 0; i < allBundles.length; i++) {
				if (! allBundles[i].isResolved()) {
					VersionConstraint[] unsatisfiedConstraint = allBundles[i].getUnsatisfiedConstraints();
					for (int j = 0; j < unsatisfiedConstraint.length; j++) {
						System.out.println(unsatisfiedConstraint[j].getName());
					}	
				}
			}
			//TODO How do we build 2.1 bundles
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
	
	/**
	 * This methods allows to set extra prerequisite for a given plugin
	 */
	private void setExtraPrerequisites() {
		BundleDescription[] bundles = state.getState().getBundles();
		for (int i = 0; i < bundles.length; i++) {
			addPrerequisites(state, bundles[i]);
		}
	}

	private void addPrerequisites(PDEState state, BundleDescription model) {
		//Read the build.properties
		Properties buildProperties = new Properties();
		InputStream propertyStream = null;
		try {
			propertyStream = new URL(model.getLocation() + "/" + PROPERTIES_FILE).openStream();
			buildProperties.load(propertyStream); //$NON-NLS-1$
		} catch (Exception e) {
			return;
		} finally {
			try {
				if (propertyStream != null)
					propertyStream.close();
			} catch (IOException e1) {
				//Ignore
			}
		}

		String extraPrereqs = (String) buildProperties.get(PROPERTY_EXTRA_PREREQUISITES);
		if (extraPrereqs==null)
			return;

		//Create the new prerequisite from the list
		BundleSpecification[] oldRequires = model.getRequiredBundles();
		String[] extraPrereqsList = Utils.getArrayFromString(extraPrereqs);
		int oldRequiresLength = oldRequires==null ? 0 : oldRequires.length; 
		BundleSpecification[] newRequires = new BundleSpecification[oldRequiresLength + extraPrereqsList.length];
		if (oldRequires != null)
			System.arraycopy(oldRequires, 0, newRequires, 0, oldRequires.length);
		for (int i = 0; i < extraPrereqsList.length; i++) {
			BundleSpecification prereq = state.getFactory().createBundleSpecification(extraPrereqsList[i], null, VersionConstraint.NO_MATCH, false, false);
			newRequires[oldRequiresLength + i] = prereq; 
		}
		BundleDescription newDescription = state.getFactory().createBundleDescription(state.getNextId(), 
				model.getUniqueId(), 
				model.getVersion(), 
				model.getLocation(), 
				newRequires,
				model.getHost(), 
				model.getPackages(), 
				model.getProvidedPackages());
	
		state.getState().removeBundle(model);
		state.addBundleDescription(newDescription);
	}

}
