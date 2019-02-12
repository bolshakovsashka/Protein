package protein.steps;

import com.intellij.ide.Prefs;
import com.intellij.ui.wizard.WizardNavigationState;
import com.intellij.ui.wizard.WizardStep;
import protein.AddComponentWizardModel;
import protein.common.Settings;
import protein.common.SettingsManager;
import protein.kotlinbuilders.KotlinApiBuilder;
import protein.kotlinbuilders.ProteinApiConfiguration;
import protein.tracking.BugsnagErrorTracking;
import protein.tracking.ErrorTracking;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.prefs.Preferences;

import static protein.common.StorageUtils.toFirstUpperCase;

public class PackageInfoStep extends WizardStep<AddComponentWizardModel> {
  private JPanel rootPanel;
  private JPanel headerPanel;
  private JPanel swaggerPanel;
  private JPanel agentPanel;
  private JLabel header;
  private JTextField additionalConfigTextField;
  private JTextField serviceEndPointTextField;
  private JLabel packageNameLabel;
  private JTextField componentNameTextField;
  private JTextField domainTextField;
  private JTextField swaggerUrlTextField2;
  private ErrorTracking errorTracking = new BugsnagErrorTracking();

  @Override
  public JComponent prepare(WizardNavigationState wizardNavigationState) {
    rootPanel.revalidate();
    if (Settings.getInstance().getComponentName() != null && !"".equals(Settings.getInstance().getComponentName())) {
      componentNameTextField.setText(Settings.getInstance().getComponentName());
      domainTextField.setText(Settings.getInstance().getDomainName());
      swaggerUrlTextField2.setText(Settings.getInstance().getSwaggerUrl());
      serviceEndPointTextField.setText(Settings.getInstance().getServiceEndpoint());
      additionalConfigTextField.setText(Settings.getInstance().getAdditionalConfig());
    }

    setKeyListeners();
    setWizardFinishButtonProperties(wizardNavigationState);

    return rootPanel;
  }

  private void setWizardFinishButtonProperties(WizardNavigationState wizardNavigationState) {
    swaggerUrlTextField2.addKeyListener(new KeyListener() {
      @Override
      public void keyTyped(KeyEvent e) {
        checkIfCanEnableFinishButton(wizardNavigationState);
      }

      @Override
      public void keyPressed(KeyEvent e) {
        checkIfCanEnableFinishButton(wizardNavigationState);
      }

      @Override
      public void keyReleased(KeyEvent e) {
        checkIfCanEnableFinishButton(wizardNavigationState);
      }
    });
    wizardNavigationState.FINISH.setName("СДЕЛАТЬ ЗАЕБИСЬ");
    if (!canFinish()) {
      wizardNavigationState.FINISH.setEnabled(false);
    }
  }

  private void setKeyListeners() {
    KeyListener textChangeKeyListener = new KeyListener() {
      @Override
      public void keyTyped(KeyEvent e) {
        packageNameLabel.setText(domainTextField.getText() + "." + componentNameTextField.getText());
      }

      @Override
      public void keyPressed(KeyEvent e) {
        packageNameLabel.setText(domainTextField.getText() + "." + componentNameTextField.getText());
      }

      @Override
      public void keyReleased(KeyEvent e) {
        packageNameLabel.setText(domainTextField.getText() + "." + componentNameTextField.getText());
      }
    };
    componentNameTextField.addKeyListener(textChangeKeyListener);
    domainTextField.addKeyListener(textChangeKeyListener);
  }

  private void checkIfCanEnableFinishButton(WizardNavigationState wizardNavigationState) {
    if (canFinish()) {
      wizardNavigationState.FINISH.setEnabled(true);
    }
  }

  @Override
  public boolean onFinish() {
    if (canFinish()) {
      updateSettingsValues();
      saveSettings();

      buildKotlinApi();
    }
    return super.onFinish();
  }

  private boolean canFinish() {
    return !"".equals(this.componentNameTextField.getText())
      && !"".equals(this.domainTextField.getText())
      && !"".equals(this.swaggerUrlTextField2.getText());
  }

  private void saveSettings() {
    SettingsManager.getInstance().add(Settings.getInstance());
    SettingsManager.getInstance().save();
  }

  private void updateSettingsValues() {
    Settings.getInstance().setComponentName(this.componentNameTextField.getText());
    Settings.getInstance().setDomainName(this.domainTextField.getText());
    Settings.getInstance().setSwaggerUrl(this.swaggerUrlTextField2.getText());
    Settings.getInstance().setServiceEndpoint(this.serviceEndPointTextField.getText());
    Settings.getInstance().setAdditionalConfig(this.additionalConfigTextField.getText());
  }

  private void buildKotlinApi() {
    ProteinApiConfiguration configuration = new ProteinApiConfiguration(
      this.serviceEndPointTextField.getText(),
      this.swaggerUrlTextField2.getText(),
      Settings.getInstance().getPackageName(),
      toFirstUpperCase(this.componentNameTextField.getText()),
      Settings.getInstance().getModuleName(),
      "",
      this.additionalConfigTextField.getText()
    );
    try {
      KotlinApiBuilder kotlinApiBuilder = new KotlinApiBuilder(configuration, errorTracking);
      kotlinApiBuilder.build();
      kotlinApiBuilder.generateFiles();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
