package org.eclipse.pde.internal.build.site;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;

import org.eclipse.core.runtime.Platform;
import org.eclipse.osgi.framework.log.FrameworkLog;
import org.eclipse.osgi.framework.util.Headers;
import org.eclipse.osgi.service.pluginconversion.PluginConverter;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.BundleSpecification;
import org.eclipse.osgi.service.resolver.PackageSpecification;
import org.eclipse.osgi.service.resolver.State;
import org.eclipse.osgi.service.resolver.StateObjectFactory;
import org.eclipse.osgi.service.resolver.Version;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;

// This class provides a higher level API on the state
public class PDEState {
	static private BundleContext ctx;
	private StateObjectFactory factory;
	private State state;
	private long id;
	int targetVersion = 2;
	
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
			Headers manifest = Headers.parseManifest(manifestStream);
			BundleDescription descriptor = factory.createBundleDescription(manifest, "file:"+bundleLocation.getParentFile().getParent(), id++);	//FIXME This is dangereous
			//TODO We need to handle the special case of the osgi bundle for whom the bundle-classpath is specified in the eclipse.properties file in the osgi folder 
//			if (((String) manifest.get(Constants.BUNDLE_GLOBALNAME)).equals("org.eclipse.osgi")) {
//				
//				classpathmanifest.put(Constants.BUNDLE_CLASSPATH, "core.jar, console.jar, osgi.jar, resolver.jar, defaultAdaptor.jar, eclipseAdaptor.jar");
//			}
			descriptor.setUserObject(manifest);
			state.addBundle(descriptor);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		} catch (BundleException be) {
			be.printStackTrace();
			return false;
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
	
	public void addBundles(URL[] bundles) {
		//TODO Need to add system bundles
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
}
