
package org.eclipse.pde.internal.build.site;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.model.*;
import org.eclipse.osgi.service.resolver.*;
import org.eclipse.pde.internal.build.IPDEBuildConstants;
import org.eclipse.pde.internal.build.Policy;
import org.osgi.framework.Constants;

public class PluginRegistryConverter {
	private PluginRegistryModel registry;
	private URL[] paths;
	
	public PluginRegistryConverter(URL[] paths) throws CoreException {
		this.paths = paths;
		registry = getPluginRegistry();
	}
	
	public PluginRegistryModel getPluginRegistry() throws CoreException {
		if (registry == null) {
			// create the registry according to the site where the code to compile is, and a existing installation of eclipse 
			MultiStatus problems = new MultiStatus(IPDEBuildConstants.PI_PDEBUILD, IPDEBuildConstants.EXCEPTION_MODEL_PARSE, Policy.bind("exception.pluginParse"), null); //$NON-NLS-1$
			Factory factory = new Factory(problems);
			registry = PluginRegistryModel.parsePlugins(paths, factory);
			registry.resolve(true, false);
			IStatus status = factory.getStatus();
			if (!status.isOK())
				throw new CoreException(status);
		}
		return registry;
	}
	
	public void addRegistryToState(PDEState state) {
		PluginModel[] plugins = registry.getPlugins();
		PluginFragmentModel[] fragments = registry.getFragments();
		
		for (int i = 0; i < plugins.length; i++) {		
			BundleDescription bd = state.getFactory().createBundleDescription(state.getNextId(), plugins[i].getPluginId(), new Version(plugins[i].getVersion()), plugins[i].getLocation(), createBundleSpecification(plugins[i].getRequires(), state) , null, null, null);
			String libs = createClasspath(plugins[i].getRuntime());
			Properties manifest = new Properties();
			if(libs != null)
				manifest.put(Constants.BUNDLE_CLASSPATH, libs);
			try {
				state.loadPropertyFileIn(manifest, new URL(fragments[i].getLocation()));
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			bd.setUserObject(manifest);
			state.addBundleDescription(bd);
		}
	
		for (int i = 0; i < fragments.length; i++) {
			HostSpecification host = state.getFactory().createHostSpecification(fragments[i].getPluginId(), new Version(fragments[i].getPluginVersion()), fragments[i].getMatch(), false);
			BundleDescription bd = state.getFactory().createBundleDescription(state.getNextId(), fragments[i].getId(), new Version(fragments[i].getVersion()), fragments[i].getLocation(), createBundleSpecification(fragments[i].getRequires(), state) , host, null, null);
			String libs = createClasspath(fragments[i].getRuntime());
			Properties manifest = new Properties();
			if(libs != null)
				manifest.put(Constants.BUNDLE_CLASSPATH, libs);
			try {
				state.loadPropertyFileIn(manifest, new URL(fragments[i].getLocation()));
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			bd.setUserObject(manifest);
			state.addBundleDescription(bd);
		}
	}
	
	private BundleSpecification[] createBundleSpecification(PluginPrerequisiteModel[] prereqs, PDEState state) {
		if (prereqs == null)
			return new BundleSpecification[0];
		BundleSpecification[] specs = new BundleSpecification[prereqs.length];
		for (int i = 0; i < prereqs.length; i++) {
			specs[i] = state.getFactory().createBundleSpecification(prereqs[i].getPlugin(), new Version(prereqs[i].getVersion()), prereqs[i].getMatchByte(), prereqs[i].getExport(), prereqs[i].getOptional() );
		}
		return specs;
	}

	private String createClasspath(LibraryModel[] libs) {
		if (libs == null || libs.length == 0)
			return null;
		
		String result = "";
		for (int i = 0; i < libs.length; i++) {
			result += libs[i].getName() + (i == libs.length-1 ? "" : ","); 
		}
		return result;
	}
}
