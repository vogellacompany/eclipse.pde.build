package org.eclipse.pde.internal.build.site;

import java.io.*;
import java.net.URL;
import java.util.*;
import org.eclipse.core.runtime.Platform;
import org.eclipse.osgi.framework.log.FrameworkLog;
import org.eclipse.osgi.framework.util.Headers;
import org.eclipse.osgi.service.pluginconversion.PluginConverter;
import org.eclipse.osgi.service.resolver.*;
import org.eclipse.pde.internal.build.*;
import org.eclipse.pde.internal.build.IPDEBuildConstants;
import org.eclipse.pde.internal.build.Utils;
import org.osgi.framework.*;

// This class provides a higher level API on the state
public class PDEState implements IPDEBuildConstants, IXMLConstants {
	static private BundleContext ctx;
	private StateObjectFactory factory;
	private State state;
	private long id;
	private Properties repositoryVersions;
	private ServiceReference logServiceReference;
	private ServiceReference converterServiceReference;
	
	protected long getNextId() {
		return ++id;
	}
		
	public PDEState() {
		factory = Platform.getPlatformAdmin().getFactory();
		state = factory.createState();
		state.setResolver(Platform.getPlatformAdmin().getResolver());
		id = 0;
		loadPluginVersionFile();
	}
	
	public StateObjectFactory getFactory() {
		return factory;
	}

	public void addBundleDescription(BundleDescription toAdd) {
		state.addBundle(toAdd);
	}
	
	private FrameworkLog acquireFrameworkLogService() throws Exception{
		logServiceReference = ctx.getServiceReference(FrameworkLog.class.getName());
		if (logServiceReference == null)
			return null;
		return (FrameworkLog) ctx.getService(logServiceReference);
	}
	
	private PluginConverter acquirePluginConverter() throws Exception{
		converterServiceReference = ctx.getServiceReference(PluginConverter.class.getName());
		if (converterServiceReference == null)
			return null;
		return (PluginConverter) ctx.getService(converterServiceReference);
	}
	
	public boolean addBundle(Dictionary enhancedManifest, URL bundleLocation) {
		updateVersionNumber(enhancedManifest);
		try {
			BundleDescription descriptor;
			descriptor = factory.createBundleDescription(enhancedManifest, bundleLocation.toExternalForm(), getNextId());
			descriptor.setUserObject(enhancedManifest);
			state.addBundle(descriptor);
			setExtraPrerequisites(descriptor, enhancedManifest);
		} catch (BundleException e) {
			//TODO Need to log
			return false;
		}
		return true;
	}

	private void loadPluginVersionFile() {
		repositoryVersions = new Properties();
		FileInputStream input;
		try {
			input = new FileInputStream(AbstractScriptGenerator.getWorkingDirectory() + "/" + DEFAULT_PLUGIN_VERSION_FILENAME_DESCRIPTOR); //$NON-NLS-1$
			repositoryVersions.load(input);
		} catch (IOException e) {
			//Ignore
		}
	}
	private void updateVersionNumber(Dictionary manifest) {
		String q = (String) manifest.get(PROPERTY_QUALIFIER);
		if (q==null)
			return;
		String newQualifier = null;
		if (q.equalsIgnoreCase(PROPERTY_CONTEXT)) {
			newQualifier = (String) repositoryVersions.get(manifest.get(Constants.BUNDLE_SYMBOLICNAME));
			if (newQualifier==null)
				newQualifier = "" + Calendar.getInstance().get(Calendar.YEAR) + Calendar.getInstance().get(Calendar.MONTH) +Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
		} else {
			newQualifier = q;
		}
		if (newQualifier==null)
			return;
		String oldVersion = (String) manifest.get(Constants.BUNDLE_VERSION);
		manifest.put(Constants.BUNDLE_VERSION, oldVersion.replaceFirst(PROPERTY_QUALIFIER, newQualifier));
	}

	private void setExtraPrerequisites(BundleDescription descriptor, Dictionary manifest) {
		String extraPrereqs = (String) manifest.get(PROPERTY_EXTRA_PREREQUISITES);
		if (extraPrereqs==null)
			return;

		//Create the new prerequisite from the list
		BundleSpecification[] oldRequires = descriptor.getRequiredBundles();
		String[] extraPrereqsList = Utils.getArrayFromString(extraPrereqs);
		int oldRequiresLength = oldRequires==null ? 0 : oldRequires.length; 
		BundleSpecification[] newRequires = new BundleSpecification[oldRequiresLength + extraPrereqsList.length];
		if (oldRequires != null)
			System.arraycopy(oldRequires, 0, newRequires, 0, oldRequires.length);
		for (int i = 0; i < extraPrereqsList.length; i++) {
			BundleSpecification prereq = state.getFactory().createBundleSpecification(extraPrereqsList[i], null, VersionConstraint.NO_MATCH, false, false);
			newRequires[oldRequiresLength + i] = prereq; 
		}
		BundleDescription newDescription = state.getFactory().createBundleDescription(getNextId(), 
				descriptor.getUniqueId(), 
				descriptor.getVersion(), 
				descriptor.getLocation(), 
				newRequires,
				descriptor.getHost(), 
				descriptor.getPackages(), 
				descriptor.getProvidedPackages());
		newDescription.setUserObject(descriptor.getUserObject());
		state.removeBundle(descriptor);
		state.addBundle(newDescription);
	}
	
	
	public boolean addBundle(URL bundleLocation) {
		Properties manifest;
		try {
			manifest = loadManifest(bundleLocation);
			loadPropertyFileIn(manifest, bundleLocation);
			if (manifest.getProperty(Constants.BUNDLE_SYMBOLICNAME).equals("org.eclipse.osgi")) {	
				//TODO We need to handle the special case of the osgi bundle for whom the bundle-classpath is specified in the eclipse.properties file in the osgi folder
				manifest.put(Constants.BUNDLE_CLASSPATH, "core.jar, console.jar, osgi.jar, resolver.jar, defaultAdaptor.jar, eclipseAdaptor.jar");
			}
		} catch (IOException e) {
			//TODO Need to log
			return false;
		}

		return addBundle(manifest, bundleLocation);
	}
	
	private Properties loadManifest(URL bundleLocation) throws IOException {
		InputStream stream = null;
		try {
			URL tmpLocation = new URL(bundleLocation, "META-INF/MANIFEST.MF");
			 stream = tmpLocation .openStream();
		} catch (IOException e) {
			//We do not do any manifest generation for jared plugin
			if (bundleLocation.getProtocol().equalsIgnoreCase("jar"))
				throw e; 	 
		}
		
		if (stream == null) {
			File manifestLocation = new File(bundleLocation.getFile(), "META-INF/MANIFEST.MF");
			if (! manifestLocation.exists()) {
				//TODO Here we should set a temporary location or maybe not even write the file to disk
				PluginConverter converter;
				try {
					converter = acquirePluginConverter();
					converter.convertManifest(bundleLocation, manifestLocation, false);
				} catch (Exception e) {
					e.printStackTrace(); 
				}
			}
			stream = new BufferedInputStream(new FileInputStream(manifestLocation));
		}
		try {
			Headers manifest = Headers.parseManifest(stream);
			return manifestToDictionary(manifest);
		} catch (BundleException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		return null;
		
	}

	private Properties manifestToDictionary(Dictionary d) {
		Enumeration enum = d.keys();
		Properties result = new Properties();
		while (enum.hasMoreElements()) {
			String key = (String) enum.nextElement();
			result.put(key, d.get(key));
		}
		return result;
	}
	
	public void addBundles(URL[] bundles) {
		for (int i = 0; i < bundles.length; i++) {
			addBundle(bundles[i]); //Need to report a better error message when the manifest is bogus
		}
	}
	
	public void resolveState() {
		state.resolve(false);
	}

	public static void setCtx(BundleContext ctx) {
		PDEState.ctx = ctx;
	}
	
	public State getState() {
		return state;
	}
	
	public BundleDescription[] getDependentBundles(String bundleId, Version version) {
		BundleDescription root = state.getBundle(bundleId, version);
		return getDependentBundles(root);
	}

	/**
	 * This methods return the bundleDescriptions to which imports have been bound to.
	 * @param bundleId
	 * @param version
	 * @return 
	 */
	public static BundleDescription[] getImportedBundles(BundleDescription root) {
		if (root==null)
			return new BundleDescription[0];
		
		PackageSpecification[] packages = root.getPackages();
		ArrayList resolvedImported = new ArrayList(packages.length); 
		for (int i = 0; i < packages.length; i++) {
			if(!packages[i].isExported() && packages[i].isResolved() && ! resolvedImported.contains(packages[i].getSupplier()))
				resolvedImported.add(packages[i].getSupplier());
		}
		BundleDescription[] result = new BundleDescription[resolvedImported.size()];
		return (BundleDescription[]) resolvedImported.toArray(result);
	}
	
	/**
	 * This methods return the bundleDescriptions to which required bundles have been bound to.
	 * @param bundleId
	 * @param version
	 * @return
	 */
	public static BundleDescription[] getRequiredBundles(BundleDescription root) {
		if (root==null)
			return new BundleDescription[0];
		
		BundleSpecification[] required = root.getRequiredBundles();
		ArrayList resolvedRequired = new ArrayList(required.length); 
		for (int i = 0; i < required.length; i++) {
			if(required[i].isResolved() && ! resolvedRequired.contains(required[i].getSupplier()))
				resolvedRequired.add(required[i].getSupplier());
		}
		BundleDescription[] result = new BundleDescription[resolvedRequired.size()];
		return (BundleDescription[]) resolvedRequired.toArray(result);
	}
	
	public BundleDescription getResolvedBundle(String bundleId, String version) {
		if (version == null)
			return getResolvedBundle(bundleId);
		BundleDescription description = getState().getBundle(bundleId, new Version(version));
		if (description.isResolved())
			return description;
		return null;
	}
	
	public BundleDescription getResolvedBundle(String bundleId) {
		BundleDescription[] description = getState().getBundles(bundleId);
		if (description == null)
			return null;
		
		for (int i = 0; i < description.length; i++) {
			if (description[i].isResolved())
				return description[i];
		}
		return null;
	}

	public static BundleDescription[] getDependentBundles(BundleDescription root) {
		BundleDescription[] imported = getImportedBundles(root);
		BundleDescription[] required = getRequiredBundles(root);
		BundleDescription[] dependents = new BundleDescription[imported.length + required.length];
		System.arraycopy(imported, 0, dependents, 0, imported.length);
		System.arraycopy(required, 0, dependents, imported.length, required.length);
		return dependents;
	}

	public static BundleDescription[] getDependentBundlesWithFragments(BundleDescription root) {
		BundleDescription[] imported = getImportedBundles(root);
		BundleDescription[] importedByFragments = getImportedByFragments(root);
		BundleDescription[] required = getRequiredBundles(root);
		BundleDescription[] requiredByFragments = getRequiredBundles(root);
		BundleDescription[] dependents = new BundleDescription[imported.length + importedByFragments.length + required.length + requiredByFragments.length];
		System.arraycopy(imported, 0, dependents, 0, imported.length);
		System.arraycopy(importedByFragments, 0, dependents, imported.length, importedByFragments.length);
		System.arraycopy(required, 0, dependents, imported.length + importedByFragments.length, required.length);
		System.arraycopy(requiredByFragments, 0, dependents, imported.length + importedByFragments.length + required.length, requiredByFragments.length);
		return dependents;
	}
	
	public static BundleDescription[] getImportedByFragments(BundleDescription root) {
		BundleDescription[] fragments = root.getFragments();
		List importedByFragments = new ArrayList();
		for (int i = 0; i < fragments.length; i++) {
			if (!fragments[i].isResolved())
				continue;
			merge(importedByFragments, getImportedBundles(fragments[i]));
		}
		BundleDescription[] result = new BundleDescription[importedByFragments.size()];
		return (BundleDescription[]) importedByFragments.toArray(result);
	}
	
	public static BundleDescription[] getRequiredByFragments(BundleDescription root) {
		BundleDescription[] fragments = root.getFragments();
		List importedByFragments = new ArrayList();
		for (int i = 0; i < fragments.length; i++) {
			if (!fragments[i].isResolved())
				continue;
			merge(importedByFragments, getRequiredBundles(fragments[i]));
		}
		BundleDescription[] result = new BundleDescription[importedByFragments.size()];
		return (BundleDescription[]) importedByFragments.toArray(result);
	}
		
	public static void merge(List source, BundleDescription[] toAdd) {
		for (int i = 0; i < toAdd.length; i++) {
			if(! source.contains(toAdd[i]))
				source.add(toAdd[i]);
		}
	}
	
	public void loadPropertyFileIn(Properties toMerge, URL location) {
		InputStream propertyStream = null;
		try {
			propertyStream = new URL(location.toExternalForm()+ "/" + PROPERTIES_FILE).openStream();
			toMerge.load(propertyStream); //$NON-NLS-1$
		} catch (Exception e) {
			//ignore because compiled plug-ins do not have such files
		} finally {
			try {
				if (propertyStream != null)
					propertyStream.close();
			} catch (IOException e1) {
				//Ignore
			}
		}
	}
}
