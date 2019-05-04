package protein.common;

import com.bugsnag.Bugsnag;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.squareup.kotlinpoet.FileSpec;
import com.squareup.kotlinpoet.TypeSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.ListModel;
import javax.swing.DefaultListModel;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class StorageUtils {

  @NotNull
  public static ListModel getFoldersList() {
    DefaultListModel listModel = new DefaultListModel();
    Project currentProject = (Project) DataManager.getInstance().getDataContext().getData(DataConstants.PROJECT);
    if (currentProject != null) {
      VirtualFile[] contentRoots = ProjectRootManager.getInstance(currentProject).getContentRoots();
      for (VirtualFile virtualFile : contentRoots) {
        if (virtualFile.isDirectory() && virtualFile.isWritable()) {
          listModel.addElement(virtualFile.getName());
        }
      }

      VirtualFile[] contentRootsFromAllModules = ProjectRootManager.getInstance(currentProject).getContentRootsFromAllModules()[0].getChildren();
      for (VirtualFile virtualFile : contentRootsFromAllModules) {
        if (virtualFile.isDirectory() && virtualFile.isWritable()) {
          listModel.addElement(virtualFile.getName());
        }
      }
    }
    return listModel;
  }

  public static void generateFiles(@Nullable String customPath, @Nullable String moduleName, @Nullable String packageName, @Nullable String subPackage, @NotNull com.squareup.kotlinpoet.TypeSpec classTypeSpec, String[] imports) {
    try {
      FileSpec.Builder builder = FileSpec.builder(subPackage != null ? (packageName + "." + subPackage) : packageName, classTypeSpec.getName());
      if (imports.length != 0) {
        builder.addImport(packageName, imports);
      }
      FileSpec kotlinFile = builder
        .addType(classTypeSpec)
        .build();
      String projectPath;
      if (customPath == null) {
        Project currentProject = (Project) DataManager.getInstance().getDataContext().getData(DataConstants.PROJECT);
        if (moduleName != null && !"".equals(moduleName)) {
          projectPath = FileEditorManager.getInstance(currentProject).getProject().getBasePath() + "/" + moduleName + "/src/main/java/";
        } else {
          projectPath = FileEditorManager.getInstance(currentProject).getProject().getBasePath() + "/src/main/java/";
        }
      } else {
        if (moduleName != null && !"".equals(moduleName)) {
          projectPath = customPath + "/" + moduleName + "/src/main/java/";
        } else {
          projectPath = customPath + "/src/main/java/";
        }
      }
      Path path = FileSystems.getDefault().getPath(projectPath);
      if (!Files.exists(path)) {
        Files.createDirectories(path);
      }
      kotlinFile.writeTo(path);
      kotlinFile.writeTo(System.out);
    } catch (IOException e) {
      e.printStackTrace();
      trackError(e);
    }
  }

  public static List<String> generateString(@Nullable String packageName, @NotNull List<TypeSpec> classTypeSpec) {
    ArrayList<String> strings = new ArrayList<>();
    for (TypeSpec typeSpec : classTypeSpec) {
      strings.add(FileSpec.builder(packageName, typeSpec.getName()).addType(typeSpec).build().toString());
    }
    return strings;
  }

  public static String toFirstUpperCase(String text) {
    return text.substring(0, 1).toUpperCase() + text.substring(1);
  }

  public static String toFirstLowerCase(String text) {
    return text.substring(0, 1).toLowerCase() + text.substring(1);
  }

  private static void trackError(IOException e) {
    Bugsnag bugSnag = new Bugsnag("a33c387e14e810eace96242d7382737d");
    bugSnag.notify(e);
  }
}
