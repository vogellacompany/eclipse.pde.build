package org.eclipse.pde.internal.build.site;

import java.io.*;
import java.net.URL;
import java.util.*;
import org.eclipse.core.runtime.Platform;
import org.eclipse.osgi.framework.log.FrameworkLog;
import org.eclipse.osgi.framework.util.Headers;
import org.eclipse.osgi.service.pluginconversion.PluginConverter;
import org.eclipse.osgi.service.resolver.*;
import org.osgi.framework.*;

// This class provides a higher level API on the state
public class PDEState {
	static private BundleContext ctx;
	private StateObjectFactory factory;
	private State state;
	private long id;
	
	private ServiceReference logServiceReference;
	private ServiceReference converterServiceReference;
	
	public PDEState() {
		factory = Platform.getPlatformAdmin().getFactory();
		state = factory.createState();
		state.setResolver(Platform.getPlatformAdmin().getResolver());
		id = 0;
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
	
	public boolean addBundle(File bundleLocation) {
		//TODO This code will have problem if we want to build out of jars
		File manifestLocation = null;
		if (bundleLocation.getName().equalsIgnoreCase("plugin.xml") || bundleLocation.getName().equalsIgnoreCase("fragment.xml")) {
			manifestLocation = new File(bundleLocation.getParentFile(), "META-INF");
			manifestLocation.mkdirs();
			manifestLocation = new File(manifestLocation, "MANIFEST.MF");
			PluginConverter converter;
			try {
				converter = acquirePluginConverter();
				converter.convertManifest(bundleLocation, manifestLocation);	//TODO the generated manifest does not really need to be written to disk.
				bundleLocation = manifestLocation;
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}
		InputStream manifestStream = null;
		try {
			manifestStream = new BufferedInputStream(new FileInputStream(bundleLocation));
			Dictionary manifest = Headers.parseManifest(manifestStream);
			if (((String) manifest.get(Constants.BUNDLE_GLOBALNAME)).equals("org.eclipse.osgi")) {
				manifest = manifestToDictionary(manifest);
				//TODO We need to handle the special case of the osgi bundle for whom the bundle-classpath is specified in the eclipse.properties file in the osgi folder
				manifest.put(Constants.BUNDLE_CLASSPATH, "core.jar, console.jar, osgi.jar, resolver.jar, defaultAdaptor.jar, eclipseAdaptor.jar");
			}
			BundleDescription descriptor = factory.createBundleDescription(manifest, "file:"+bundleLocation.getParentFile().getParent(), id++);	//FIXME This is dangereous
			descriptor.setUserObject(manifest);
			state.addBundle(descriptor);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		} catch (BundleException be) {
			be.printStackTrace();
			return false;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (manifestStream != null)
				try {
					manifestStream.close();
				} catch (IOException e1) {
					//IGNORE
				}
		}
		return true;
	}
	
	public Dictionary manifestToDictionary(Dictionary d) {
		Enumeration enum = d.keys();
		Hashtable result = new Hashtable();
		while (enum.hasMoreElements()) {
			String key = (String) enum.nextElement();
			result.put(key, d.get(key));
		}
		return result;
	}
	public void addBundles(URL[] bundles) {
		for (int i = 0; i < bundles.length; i++) {
			addBundle(new File(bundles[i].getFile())); //Need to report a better error message when the manifest is bogus
		}
	}
	
	public void resolveState() {
		state.resolve(false);
	}
	/**
	 * @param ctx The ctx to set.
	 */
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
}
