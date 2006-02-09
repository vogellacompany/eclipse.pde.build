package org.eclipse.pde.internal.ui.nls;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.pde.core.IModel;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.internal.core.PDEManager;
import org.eclipse.pde.internal.ui.elements.DefaultElement;


public class ModelChange extends DefaultElement {
	
	private static final String DEFAULT_LOCALIZATION_PREFIX = "plugin"; //$NON-NLS-1$
	public static final String LOCALIZATION_FILE_SUFFIX = ".properties"; //$NON-NLS-1$
	
	private ModelChangeFile fXMLCoupling;
	private ModelChangeFile fMFCoupling;
	
	private IPluginModelBase fParent;
	private boolean fPreSelected;
	
	private String fBundleLocalization;
	private IFile fPropertiesFile;
	private Properties fProperties;
	
	protected static boolean modelLoaded(IModel model) {
		try {
			model.load();
		} catch (CoreException e) {
		}
		return (model.isLoaded());
	}
	
	public ModelChange(IPluginModelBase parent, boolean preSelected) {
		fParent = parent;
		fPreSelected = preSelected;
		fBundleLocalization = PDEManager.getBundleLocalization(fParent);
		if (fBundleLocalization == null)
			fBundleLocalization = DEFAULT_LOCALIZATION_PREFIX;
	}
	
	public void addChange(IFile file, ModelChangeElement change) {
		if (change == null || file == null)
			return;
		String ext = file.getFileExtension();
		if (ext.equalsIgnoreCase("xml")) //$NON-NLS-1$
			addXMLChange(file, change);
		else if (ext.equalsIgnoreCase("MF")) //$NON-NLS-1$
			addMFChange(file, change);
		else
			return;
	}
	
	private void addXMLChange(IFile file, ModelChangeElement change) {
		if (fXMLCoupling == null) {
			fXMLCoupling = new ModelChangeFile(file, this);
		}
		if (!fXMLCoupling.getFile().equals(file)) {
			return;
		}
		fXMLCoupling.add(change);
	}
	
	private void addMFChange(IFile file, ModelChangeElement change) {
		if (fMFCoupling == null) {
			fMFCoupling = new ModelChangeFile(file, this);
		}
		fMFCoupling.add(change);
	}
	
	public IFile[] getChangeFiles() {
		IFile xmlFile = fXMLCoupling != null ? fXMLCoupling.getFile() : null;
		IFile mfFile = fMFCoupling != null ? fMFCoupling.getFile() : null;
		if (xmlFile != null && mfFile != null)
			return new IFile[] {xmlFile, mfFile};
		if (xmlFile != null)
			return new IFile[] {xmlFile};
		if (mfFile != null)
			return new IFile[] {mfFile};
		return new IFile[0];
	}
	
	public IFile getPropertiesFile() {
		IProject project = fParent.getUnderlyingResource().getProject();
		if (fBundleLocalization.indexOf(LOCALIZATION_FILE_SUFFIX) == -1)
			fPropertiesFile = project.getFile(fBundleLocalization + LOCALIZATION_FILE_SUFFIX);
		else
			fPropertiesFile = project.getFile(fBundleLocalization);
		return fPropertiesFile;
	}
	
	public Properties getProperties() {
		if (fPropertiesFile == null)
			getPropertiesFile();
		if (fProperties == null) {
			try {
				fProperties = new Properties();
				if (fPropertiesFile != null && fPropertiesFile.exists())
					fProperties.load(fPropertiesFile.getContents());
			} catch (CoreException e) {
			} catch (IOException e) {
			}
		}
		return fProperties;
	}
	
	public ArrayList getChangesInFile(IFile file) {
		if (fXMLCoupling != null && file == fXMLCoupling.getFile())
			return fXMLCoupling.getChanges();
		if (fMFCoupling != null && file == fMFCoupling.getFile())
			return fMFCoupling.getChanges();
		return null;
	}
	
	public int getNumberOfChangesInFile(IFile file) {
		if (fXMLCoupling != null && file == fXMLCoupling.getFile())
			return fXMLCoupling.getNumChanges();
		if (fMFCoupling != null && file == fMFCoupling.getFile())
			return fMFCoupling.getNumChanges();
		return 0;
	}
	
	public boolean wasPreSelected() {
		return fPreSelected;
	}
	
	public IPluginModelBase getParentModel() {
		return fParent;
	}

	public ModelChangeFile[] getModelChangeFiles() {
		if (fXMLCoupling != null && fMFCoupling != null)
			return new ModelChangeFile[] {fXMLCoupling, fMFCoupling};
		if (fXMLCoupling != null)
			return new ModelChangeFile[] {fXMLCoupling};
		if (fMFCoupling != null)
			return new ModelChangeFile[] {fMFCoupling};
		return new ModelChangeFile[0];
	}
	
	public void setBundleLocalization(String bundleLocalization) {
		fBundleLocalization = bundleLocalization;
	}
	public String getBundleLocalization() {
		return fBundleLocalization;
	}
	
	public boolean localizationSet() {
		String localization = PDEManager.getBundleLocalization(fParent);
		return localization != null && localization.length() > 0;
	}
}
