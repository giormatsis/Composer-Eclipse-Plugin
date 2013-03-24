package com.dubture.composer.ui.wizard.project;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.dltk.core.DLTKCore;
import org.eclipse.dltk.core.IModelElement;
import org.eclipse.dltk.ui.DLTKUIPlugin;
import org.eclipse.dltk.ui.wizards.NewElementWizard;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.php.internal.core.PHPVersion;
import org.eclipse.php.internal.core.facet.PHPFacets;
import org.eclipse.php.internal.core.facet.PHPFacetsConstants;
import org.eclipse.php.internal.core.project.ProjectOptions;
import org.eclipse.php.internal.ui.wizards.WizardModel;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.wizards.newresource.BasicNewProjectResourceWizard;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;
import org.eclipse.wst.common.project.facet.core.IProjectFacet;
import org.eclipse.wst.common.project.facet.core.ProjectFacetsManager;

import com.dubture.composer.core.facet.ComposerFacetConstants;
import com.dubture.composer.core.log.Logger;
import com.dubture.composer.ui.ComposerUIPluginImages;

@SuppressWarnings("restriction")
public class ComposerProjectCreationWizard extends NewElementWizard implements INewWizard, IExecutableExtension {

	public static final String SELECTED_PROJECT = "SelectedProject";
	
	private ComposerProjectWizardFirstPage firstPage;
	private ComposerProjectWizardSecondPage secondPage;
	private ComposerProjectWizardSecondPage lastPage;
	
	private IConfigurationElement config;

	public ComposerProjectCreationWizard() {
		setDefaultPageImageDescriptor(ComposerUIPluginImages.CREATE_PROJECT);
		setDialogSettings(DLTKUIPlugin.getDefault().getDialogSettings());
		setWindowTitle("New Composer Project");
	}
	
	public void addPages() {
		super.addPages();
		
		firstPage = new ComposerProjectWizardFirstPage();
		addPage(firstPage);
		
		secondPage = new ComposerProjectWizardSecondPage(firstPage);
		addPage(secondPage);
		
		lastPage = secondPage;
	}
	
	
	@Override
	public void setInitializationData(IConfigurationElement config, String propertyName, Object data)
			throws CoreException {
		this.config = config;
	}

	@Override
	protected void finishPage(IProgressMonitor monitor) throws InterruptedException, CoreException {
		
		if (firstPage != null) {
			firstPage.performFinish(monitor);
		}
		if (secondPage != null) {
			secondPage.performFinish(monitor);
		}
	}

	@Override
	public IModelElement getCreatedElement() {
		return DLTKCore.create(firstPage.getProjectHandle());		
	}
	
	@Override
	public boolean performFinish() {
		
		boolean res = super.performFinish();
		if (res) {
			
			BasicNewProjectResourceWizard.updatePerspective(config);
			selectAndReveal(lastPage.getScriptProject().getProject());

			
			
			IProject project = lastPage.getScriptProject().getProject();
			PHPVersion version = firstPage.getPHPVersionValue();
			if (version == null) {
				version = ProjectOptions.getDefaultPhpVersion();
			}
			installFacets(project, version);
			
			WizardModel model = firstPage.getWizardData();

			Object eanblement = null;
			if (model != null) {
				eanblement = model
						.getObject("REMOTE_GROUP_REMOTE_PROJECT_ENABLED");
			}

			if (model != null && eanblement != null && (Boolean) eanblement) {

				model.putObject(SELECTED_PROJECT, lastPage.getScriptProject()
						.getProject());

				IRunnableWithProgress run = (IRunnableWithProgress) Platform
						.getAdapterManager().getAdapter(model,
								IRunnableWithProgress.class);

				if (run != null) {
					try {
						getContainer().run(true, false, run);
					} catch (InvocationTargetException e) {
						handleFinishException(getShell(), e);
						return false;
					} catch (InterruptedException e) {
						return false;
					}
				}
			}
		}
		
		return res;
	}
	
	protected void installFacets(IProject project, PHPVersion version) {
		try {
			
			NullProgressMonitor monitor = new NullProgressMonitor();
			
			final IFacetedProject facetedProject = ProjectFacetsManager.create(project, true, monitor);
			final Set<IProjectFacet> fixedFacets = new HashSet<IProjectFacet>();
			IProjectFacet coreFacet = ProjectFacetsManager.getProjectFacet(PHPFacetsConstants.PHP_CORE_COMPONENT);
			fixedFacets.add(coreFacet);
			
			IProjectFacet composerFacet = ProjectFacetsManager.getProjectFacet(ComposerFacetConstants.COMPOSER_COMPONENT);
			fixedFacets.add(composerFacet);
			
			IProjectFacet phpFacet = ProjectFacetsManager.getProjectFacet(PHPFacetsConstants.PHP_COMPONENT);
			fixedFacets.add(phpFacet);
			facetedProject.setFixedProjectFacets(fixedFacets);

			// install the fixed facets
			facetedProject.installProjectFacet(coreFacet.getDefaultVersion(), null, monitor);
			facetedProject.installProjectFacet(PHPFacets.convertToFacetVersion(version), null, monitor);
			facetedProject.installProjectFacet(composerFacet.getVersion(ComposerFacetConstants.COMPOSER_COMPONENT_VERSION_1), composerFacet, monitor);
			
		} catch (CoreException ex) {
			Logger.logException(ex.getMessage(), ex);
		}
	}
}
