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
package org.eclipse.pde.internal.build;

import java.io.*;
import java.util.*;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.core.runtime.*;
import org.eclipse.pde.internal.build.ant.AntScript;
import org.eclipse.update.core.IFeature;

//FIXME This whole hierarchy of assembler needs to be polished... creation of an interface, etc...
/**
 * Generate an assemble script for a given feature and a given config. It
 * generates all the instruction to zip the listed plugins and features.
 */
public class AssembleConfigScriptGenerator extends AbstractScriptGenerator {
	protected String directory; // representing the directory where to generate the file
	protected String featureId;
	protected Config configInfo;
	protected IFeature[] features;
	protected BundleDescription[] plugins;
	protected BundleDescription[] fragments;
	protected String filename;
	protected boolean copyRootFile;
	private String PROPERTY_TMP_DIR = "tmp_dir"; //$NON-NLS-1$	
	
	public AssembleConfigScriptGenerator() {
		super();
	}

	public void initialize(String directoryName, String scriptName, String feature, Config configurationInformation, Collection pluginList, Collection fragmentList, Collection featureList, boolean rootFileCopy) throws CoreException {
		this.directory = directoryName;
		this.featureId = feature;
		this.configInfo = configurationInformation;
		this.copyRootFile = rootFileCopy;
		
		this.features = new IFeature[featureList.size()];
		featureList.toArray(this.features);

		this.plugins = new BundleDescription[pluginList.size()];
		pluginList.toArray(this.plugins);

		this.fragments = new BundleDescription[fragmentList.size()];
		fragmentList.toArray(this.fragments);

		filename = directory + "/" + (scriptName != null ? scriptName : getFilename()); //$NON-NLS-1$
		try {
			script = new AntScript(new FileOutputStream(filename));
		} catch (FileNotFoundException e) {
			// a file doesn't exist so we will create a new one
		} catch (IOException e) {
			String message = Policy.bind("exception.writingFile", filename); //$NON-NLS-1$
			throw new CoreException(new Status(IStatus.ERROR, PI_PDEBUILD, EXCEPTION_WRITING_FILE, message, e));
		}
	}

	public void generate() throws CoreException {
		generatePrologue();
		generateInitializationSteps();
		generateGatherBinPartsCalls();
		if (configInfo.getOs().equalsIgnoreCase("macosx")) { //$NON-NLS-1$
			generateTarTarget();
			generateGZipTarget();
		} else {
			generateZipTarget();
		}
		generateEpilogue();
	}

	private void generatePackagingTargets() {
		script.printTargetDeclaration("jarUp", null, null, null, "Create a jar from the given location");
		script.printZipTask("${sourceFolder}/${pluginName}.jar", "${sourceFolder}/${pluginName}", false, null);
		script.printDeleteTask("${sourceFolder}/${pluginName}", null, null);
		script.printTargetEnd();
	}

	private void generateGZipTarget() {
		script.println(
			"<move file=\"" //$NON-NLS-1$
				+ getPropertyFormat(PROPERTY_ARCHIVE_FULLPATH)
				+ "\" tofile=\"" //$NON-NLS-1$
				+ getPropertyFormat(PROPERTY_TMP_DIR)
				+ "/" //$NON-NLS-1$
				+ getPropertyFormat(PROPERTY_COLLECTING_BASE)
				+ "/tmp.tar\"/>"); //$NON-NLS-1$
		script.printGZip(
			getPropertyFormat(PROPERTY_TMP_DIR) + "/" + getPropertyFormat(PROPERTY_COLLECTING_BASE) + "/tmp.tar", //$NON-NLS-1$ //$NON-NLS-2$
			getPropertyFormat(PROPERTY_ARCHIVE_FULLPATH)); 
		List args = new ArrayList(2);
		args.add("-rf"); //$NON-NLS-1$
		args.add(getPropertyFormat(PROPERTY_TMP_DIR));
		script.printExecTask("rm", null, args, null); //$NON-NLS-1$
	}

	private void generatePrologue() {
		script.printProjectDeclaration("Assemble " + featureId, TARGET_MAIN, null); //$NON-NLS-1$
		script.printProperty(PROPERTY_ARCHIVE_NAME, computeArchiveName());
		script.printProperty(PROPERTY_OS, configInfo.getOs());
		script.printProperty(PROPERTY_WS, configInfo.getWs());
		script.printProperty(PROPERTY_ARCH, configInfo.getArch());
		script.printProperty(PROPERTY_TMP_DIR, getPropertyFormat(PROPERTY_BUILD_DIRECTORY) + "/tmp"); //$NON-NLS-1$
		script.printProperty(PROPERTY_ECLIPSE_BASE, getPropertyFormat(PROPERTY_TMP_DIR) + "/" + getPropertyFormat(PROPERTY_COLLECTING_PLACE)); //$NON-NLS-1$ //$NON-NLS-2$
		script.printProperty(PROPERTY_DESTINATION_TEMP_FOLDER, getPropertyFormat(PROPERTY_ECLIPSE_BASE) + "/" + DEFAULT_PLUGIN_LOCATION); //$NON-NLS-1$
		script.printProperty(PROPERTY_ARCHIVE_FULLPATH, getPropertyFormat(PROPERTY_BASEDIR) + "/" + getPropertyFormat(PROPERTY_BUILD_LABEL) + "/" + getPropertyFormat(PROPERTY_ARCHIVE_NAME)); //$NON-NLS-1$ //$NON-NLS-2$
		generatePackagingTargets();
		script.printTargetDeclaration(TARGET_MAIN, null, null, null, null);
	}

	private void generateInitializationSteps() {
		script.printDeleteTask(getPropertyFormat(PROPERTY_TMP_DIR), null, null);
		script.printMkdirTask(getPropertyFormat(PROPERTY_TMP_DIR));
		script.printMkdirTask(getPropertyFormat(PROPERTY_BUILD_LABEL));
	}

	private void generateGatherBinPartsCalls() throws CoreException {
		for (int i = 0; i < plugins.length; i++) {
			BundleDescription plugin = plugins[i];
			String placeToGather = getLocation(plugin);
			script.printAntTask(DEFAULT_BUILD_SCRIPT_FILENAME, Utils.makeRelative(new Path(placeToGather), new Path(workingDirectory)).toOSString(), TARGET_GATHER_BIN_PARTS, null, null, null);
			Map properties = new HashMap(2);
			properties.put("sourceFolder", "${destination.temp.folder}");
			properties.put("pluginName", plugin.getUniqueId() + "_" + plugin.getVersion());
			script.printAntCallTask("jarUp", null, properties);
		}
		for (int i = 0; i < fragments.length; i++) {
			BundleDescription fragment = fragments[i];
			String placeToGather = getLocation(fragment);
			script.printAntTask(DEFAULT_BUILD_SCRIPT_FILENAME, Utils.makeRelative(new Path(placeToGather), new Path(workingDirectory)).toOSString(), TARGET_GATHER_BIN_PARTS, null, null, null);
			Map properties = new HashMap(2);
			properties.put("sourceFolder", "${destination.temp.folder}");
			properties.put("pluginName", fragment.getUniqueId() + "_" + fragment.getVersion());
			script.printAntCallTask("jarUp", null, properties);
		}
		for (int i = 0; i < features.length; i++) {
			IFeature feature = features[i];
			String placeToGather = feature.getURL().getPath();
			int j = placeToGather.lastIndexOf(DEFAULT_FEATURE_FILENAME_DESCRIPTOR);
			if (j != -1)
				placeToGather = placeToGather.substring(0, j);
			Map properties = new HashMap(1);
			properties.put("feature.base", "${eclipse.base}");
			script.printAntTask(DEFAULT_BUILD_SCRIPT_FILENAME, Utils.makeRelative(new Path(placeToGather), new Path(workingDirectory)).toOSString(), TARGET_GATHER_BIN_PARTS, null, null, properties);
		}
	}

	private void generateEpilogue() {
		script.printTargetEnd();
		script.printProjectEnd();
		script.close();
	}

	public String getFilename() {
		return getTargetName() + ".xml"; //$NON-NLS-1$
	}

	public String getTargetName() {
		return DEFAULT_ASSEMBLE_NAME + (featureId.equals("") ? "" : ("." + featureId)) + (configInfo.equals(Config.genericConfig()) ? "" : ("." + configInfo.toStringReplacingAny(".", ANY_STRING))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
	}

	private void generateZipTarget() {
		final int parameterSize = 15;
		List parameters = new ArrayList(parameterSize + 1);
		for (int i = 0; i < plugins.length; i++) {
			parameters.add(getPropertyFormat(PROPERTY_COLLECTING_PLACE) + "/" + DEFAULT_PLUGIN_LOCATION + "/" + plugins[i].getUniqueId() + "_" + plugins[i].getVersion() + "*"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			if (i % parameterSize == 0) {
				createZipExecCommand(parameters);
				parameters.clear();
			}
		}
		if (!parameters.isEmpty()) {
			createZipExecCommand(parameters);
			parameters.clear();
		}

		for (int i = 0; i < fragments.length; i++) {
			parameters.add(getPropertyFormat(PROPERTY_COLLECTING_PLACE) + "/" + DEFAULT_PLUGIN_LOCATION + "/" + fragments[i].getUniqueId() + "_" + fragments[i].getVersion() + "*"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			if (i % parameterSize == 0) {
				createZipExecCommand(parameters);
				parameters.clear();
			}
		}
		if (!parameters.isEmpty()) {
			createZipExecCommand(parameters);
			parameters.clear();
		}

		for (int i = 0; i < features.length; i++) {
			parameters.add(getPropertyFormat(PROPERTY_COLLECTING_PLACE) + "/" + DEFAULT_FEATURE_LOCATION + "/" + features[i].getVersionedIdentifier().toString()); //$NON-NLS-1$ //$NON-NLS-2$
			if (i % parameterSize == 0) {
				createZipExecCommand(parameters);
				parameters.clear();
			}
		}
		if (!parameters.isEmpty()) {
			createZipExecCommand(parameters);
			parameters.clear();
		}

		createZipRootFileCommand();
}

	/**
	 *  Zip the root files
	 */
	private void createZipRootFileCommand() {
		if (! copyRootFile)
			return;
			
		List parameters = new ArrayList(1);
		parameters.add("-r -q ${zipargs} " + getPropertyFormat(PROPERTY_ARCHIVE_FULLPATH) + " . "); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		script.printExecTask("zip", getPropertyFormat("eclipse.base") + "/" + configInfo.toStringReplacingAny(".", ANY_STRING) + "/" + getPropertyFormat(PROPERTY_COLLECTING_BASE), parameters, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	
	}
	private void createZipExecCommand(List parameters) {
		parameters.add(0, "-r -q " + getPropertyFormat(PROPERTY_ZIP_ARGS) + " " + getPropertyFormat(PROPERTY_ARCHIVE_FULLPATH)); //$NON-NLS-1$ //$NON-NLS-2$
		script.printExecTask("zip", getPropertyFormat(PROPERTY_TMP_DIR) + "/" + getPropertyFormat(PROPERTY_COLLECTING_BASE), parameters, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	protected String computeArchiveName() {
		return featureId + "-" + getPropertyFormat(PROPERTY_BUILD_ID_PARAM) + (configInfo.equals(Config.genericConfig()) ? "" : ("-" + configInfo.toStringReplacingAny(".", ANY_STRING))) + ".zip"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
	}

	public void generateTarTarget() {
		List parameters = new ArrayList(2);
		parameters.add("-r " + getPropertyFormat(PROPERTY_TMP_DIR) + "/" + getPropertyFormat(PROPERTY_COLLECTING_BASE)  + "/" + getPropertyFormat(PROPERTY_COLLECTING_PLACE) + "/" + configInfo.toStringReplacingAny(".", ANY_STRING) + "/eclipse " + getPropertyFormat(PROPERTY_TMP_DIR) + "/" + getPropertyFormat(PROPERTY_COLLECTING_BASE)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
		script.printExecTask("cp", getPropertyFormat(PROPERTY_BASEDIR), parameters, "Linux"); //$NON-NLS-1$ //$NON-NLS-2$
		
		parameters.clear();
		parameters.add("-rf " + getPropertyFormat(PROPERTY_TMP_DIR) + "/" + getPropertyFormat(PROPERTY_COLLECTING_BASE) + "/" + getPropertyFormat(PROPERTY_COLLECTING_PLACE) + "/" +  configInfo.toStringReplacingAny(".", ANY_STRING)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		script.printExecTask("rm", getPropertyFormat(PROPERTY_BASEDIR), parameters, "Linux"); //$NON-NLS-1$ //$NON-NLS-2$
		
		parameters.clear();
		parameters.add("-cvf " + getPropertyFormat(PROPERTY_ARCHIVE_FULLPATH) + " eclipse "); //$NON-NLS-1$ //$NON-NLS-2$
		script.printExecTask("tar", getPropertyFormat(PROPERTY_TMP_DIR) + "/" +getPropertyFormat(PROPERTY_COLLECTING_BASE), parameters, "Linux"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}
}
