/**********************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.pde.internal.build.packager;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.internal.build.*;
import org.eclipse.pde.internal.build.ant.AntScript;

public class PackageScriptGenerator extends AssembleScriptGenerator {
	private String packagingPropertiesLocation;
	private String[] featureList;
	private String[] rootFiles;
	private String[] rootDirs;
	//TODO Need to change this value before releasing
	private String outputFormat = "zip"; //$NON-NLS-1$
	private boolean groupConfigs = false;
	
	
	public PackageScriptGenerator(String directory, AssemblyInformation assemblageInformation, String featureId, String scriptFilename) throws CoreException {
		super(directory, assemblageInformation, featureId, scriptFilename);
		String filename = directory + '/' + (scriptFilename == null ? ("package." + DEFAULT_ASSEMBLE_ALL) : scriptFilename); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		try {
			script = new AntScript(new FileOutputStream(filename));
		} catch (FileNotFoundException e) {
			// ignore this exception
		} catch (IOException e) {
			String message = NLS.bind(Messages.exception_writingFile, filename);
			throw new CoreException(new Status(IStatus.ERROR, PI_PDEBUILD, EXCEPTION_WRITING_FILE, message, e));
		}
		configScriptGenerator = new PackageConfigScriptGenerator();
	}
	
	public void setPropertyFile(String propertyFile) {
		packagingPropertiesLocation = propertyFile;
	}

	public void setFeatureList(String features) {
		featureList = Utils.getArrayFromString(features, ","); //$NON-NLS-1$
	}

	public void setRootFiles(String[] rootFiles) {
		this.rootFiles = rootFiles;
	}

	public void setRootDirs(String[] rootDirs) {
		this.rootDirs = rootDirs;
	}

	public void setOutput(String format) { //TODO To rename
		this.outputFormat = format;
	}

	protected void generateAssembleConfigFileTargetCall(Config aConfig) throws CoreException {
		configScriptGenerator.initialize(directory, null, featureId, aConfig, assemblageInformation.getPlugins(aConfig), assemblageInformation.getFeatures(aConfig), assemblageInformation.getRootFileProviders(aConfig));
		((PackageConfigScriptGenerator) configScriptGenerator).setPackagingPropertiesLocation(packagingPropertiesLocation);
		((PackageConfigScriptGenerator) configScriptGenerator).rootFiles(new String[0]);
		((PackageConfigScriptGenerator) configScriptGenerator).rootDirs(new String[0]);
		((PackageConfigScriptGenerator) configScriptGenerator).setOutput(outputFormat);
		configScriptGenerator.generate();

		Map params = new HashMap(1);
		params.put("assembleScriptName", configScriptGenerator.getTargetName()); //$NON-NLS-1$
		script.printAntTask(getPropertyFormat(DEFAULT_CUSTOM_TARGETS), null, computeBackwardCompatibleName(aConfig), null, null, params);
	}
	
	private String computeBackwardCompatibleName(Config configInfo) {
		return DEFAULT_ASSEMBLE_NAME + (featureId.equals("") ? "" : ('.' + featureId)) + (configInfo.equals(Config.genericConfig()) ? "" : ('.' + configInfo.toStringReplacingAny(".", ANY_STRING))) + ".xml"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
	}
}