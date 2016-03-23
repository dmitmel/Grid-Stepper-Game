package bin.start;

import bin.control.FXController;
import bin.control.MainMenuController;
import bin.util.*;
import bin.util.Stack;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import bin.settings.XMLSettingsLoader;
import bin.util.xml.XMLDumper;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main class for Plates game.
 *
 * @author Dmitriy Meleshko
 * @since v. 1.0
 *
 */
public class Main extends Application {
    /** Default width of window. */
    private static final int DEFAULT_WINDOW_WIDTH = 800;
    /** Default height of window. */
    private static final int DEFAULT_WINDOW_HEIGHT = 557;
    /** Minimal width of window. */
    private static final int MIN_WINDOW_WIDTH = 760;
    /** Minimal height of window. */
    private static final int MIN_WINDOW_HEIGHT = 557;

    /** Primary stage of application. */
    public static Stage primaryStage;
    /** FXML/{@code .prototypes} file loader. */
    private static FXMLLoader fxmlLoader = new FXMLLoader();
    /** Already loaded {@code .fxml} files from scene directory ({@code fxml}).
     * {@code key} - name of file
     * {@code value} - {@code .fxml} file. */
    private static HashMap<String, String> loadedScenes = new HashMap<>(0);

    /**
     * Getter for field {@link #exitDialog}.
     *
     * @return value of {@link #exitDialog}.
     */
    public static Alert getExitDialog() {
        return exitDialog;
    }

    /** Exit dialog object. I store this object in this class, instead of {@link MainMenuController},
     * because it is shown when shutdown hook activates. */

    private static Alert exitDialog;

    /** Exit action of exit dialog. */
    public static ButtonType EXIT_OPTION;
    /** Cancel action of exit dialog. */
    public static ButtonType CANCEL_OPTION;
    /** Available locales from {@code Locale} bundle. */
    private static ArrayList<String> availableLocales;
    /** Regexp for searching locale properties files (with extension {@code .properties}). For example, text
     * <p>{@code Locale_ch.properties}</p>
     * will be found, but text
     * <p>{@code some-file.xml}</p>
     * won't be found. */
    private static final Pattern LOCALE_RB_SEARCH_PATTERN = Pattern.compile("Locale_(\\w{2})\\.properties");
    /** Path to directory with resources. I don't put slash at the end, because it is everywhere between this constant
     * and path to certain resource. */
    public static final String RESOURCES_ROOT = "../../resources";

    /**
     * Getter for {@link #availableLocales} variable.
     *
     * @return value of {@link #availableLocales}.
     */
    @SuppressWarnings("unchecked")
    public static ArrayList<String> getAvailableLocales() {
        return (ArrayList<String>) availableLocales.clone();
    }

    public static void main(String[] args) throws Exception {
        // Shutdown hook for saving app data
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                // Some XML util classes are used at once
                DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                documentBuilderFactory.setNamespaceAware(true);
                DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();

                // Creating document for saving
                Document settingsDocument = builder.newDocument();

                Element settingsElement = settingsDocument.createElement("settings");
                settingsDocument.appendChild(settingsElement);

                for (String settingName : XMLSettingsLoader.getSettingsStorage().keySet()) {
                    Element newElement = settingsDocument.createElement("setting");
                    newElement.setAttribute("key", settingName);
                    newElement.setAttribute("value", XMLSettingsLoader.getSetting(settingName));
                    settingsElement.appendChild(newElement);
                }

                SimpleFileIO.write(XMLDumper.dump(settingsDocument),
                        Main.class.getResource(Main.RESOURCES_ROOT + "/settings/settings.xml").getPath());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        launch(args);
    }
    /**
     * Returns string value from resource bundle "Locale".
     *
     * @return string value.
     */
    public static String getLocaleStr(String key) {
        return fxmlLoader.getResources().getString(key);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Setup media and bundles
        // Class loader for bundles, uses URL location
        ClassLoader bundleClassLoader = new URLClassLoader(
                new URL[] {getClass().getResource(Main.RESOURCES_ROOT + "/bundles/")});

        fxmlLoader.setResources(ResourceBundle.getBundle("Locale",
                new Locale(XMLSettingsLoader.getSetting("lang")), bundleClassLoader, new UTF8Control()));

        // This code gets available locales
        availableLocales = new ArrayList<>(0);
        File localeRBDir = new File(getClass().getResource(Main.RESOURCES_ROOT + "/bundles/").toURI());
        for (String fileName : localeRBDir.list()) {
            Matcher fileMatcher = LOCALE_RB_SEARCH_PATTERN.matcher(fileName);
            if (fileMatcher.find()) {
                availableLocales.add(fileMatcher.group(1));
            }
        }

        EXIT_OPTION = new ButtonType(getLocaleStr("exit"), ButtonBar.ButtonData.OK_DONE);
        CANCEL_OPTION = new ButtonType(getLocaleStr("cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);


        Main.primaryStage = primaryStage;

        loadScenes();

        // Exit dialog setup
        exitDialog = new Alert(Alert.AlertType.CONFIRMATION, getLocaleStr("dialogs.body.exit-from-game"),
                EXIT_OPTION, CANCEL_OPTION);
        exitDialog.setTitle(getLocaleStr("header.base") + " - " + getLocaleStr("exit"));
        exitDialog.setHeaderText(getLocaleStr("dialogs.head.exit-from-game"));

        // Adding CSS-Stylesheet to customize dialog, for example, fonts
        exitDialog.getDialogPane().getStylesheets().add(getClass().getResource(Main.RESOURCES_ROOT + "/styles/bigger-dialog-fonts.css")
                .toExternalForm());
        exitDialog.getDialogPane().getStyleClass().add("dialog-body");

        // And, customizing dialog with setters
        Label contentLabel = (Label) exitDialog.getDialogPane().lookup(".content");
        contentLabel.setWrapText(true);

        exitDialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        exitDialog.getDialogPane().setPrefHeight(Region.USE_COMPUTED_SIZE);
        exitDialog.getDialogPane().setMaxHeight(Region.USE_PREF_SIZE);


        primaryStage.setOnCloseRequest(event -> {
            Optional result = getExitDialog().showAndWait();

            if (result.isPresent() && result.get() == EXIT_OPTION) {
                System.exit(0);
            } else if (result.isPresent() && result.get() == CANCEL_OPTION) {
                getExitDialog().hide();
                event.consume();
            }
        });


        // Changing scene to default
        changeScene("main.fxml", getLocaleStr("header.base"));
    }

    /**
     * Changes primary stage to given scene.
     *
     * @param sceneName name of scene's {@code .fxml} file
     * @param windowTitle window's title
     * @throws RuntimeException when loading fails.
     *
     */
    public static void changeScene(String sceneName, String windowTitle) {
        try {
            fxmlLoader.setController(null);
            fxmlLoader.setRoot(null);
            Parent root = fxmlLoader.load(new ByteArrayInputStream(loadedScenes.get(sceneName).getBytes()));

            FXController controller = fxmlLoader.getController();
            controller.setParent(root);
            controller.registerElements();
            controller.init();

            primaryStage.setTitle(windowTitle);
            primaryStage.setMinWidth(Main.MIN_WINDOW_WIDTH);
            primaryStage.setMinHeight(Main.MIN_WINDOW_HEIGHT);
            primaryStage.setScene(new Scene(root, Main.DEFAULT_WINDOW_WIDTH, Main.DEFAULT_WINDOW_HEIGHT));
            primaryStage.show();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads all {@code .fxml} files from scene directory ({@code fxml} and saves loaded files to {@link #loadedScenes}.
     * Why I load {@link String} file data instead of {@link Parent} object? If I had loaded {@link Parent} objects
     * parser would have activated {@code initialize} method from controllers, and this's bad for me. So, if I want
     * change scene - I just type:
     *
     * <p><code>Main.changeScene(SCENE_NAME_HERE, AND_WINDOW_TITLE_HERE);</code></p>
     *
     * Where {@link #changeScene(String, String)} changes scene such way:
     *
     * <pre><code>
     * // ...
     * fxmlLoader.setController(null);
     * fxmlLoader.setRoot(null);
     * Parent root = fxmlLoader.load(new ByteArrayInputStream(loadedScenes.get(sceneName).getBytes()));
     *
     * // ...
     * primaryStage.setScene(new Scene(root, Main.DEFAULT_WINDOW_WIDTH, Main.DEFAULT_WINDOW_HEIGHT));
     * primaryStage.show();
     * // ...
     * </code></pre>
     *
     * @throws Exception when loading fails
     *
     */
    private static void loadScenes() throws Exception {
        // Directory file-object
        File file = new File(Main.class.getResource(Main.RESOURCES_ROOT + "/fxml/").toURI());

        // Iterating all files in directory
        for (String fileName : file.list()) {
            File currentFile = new File(Main.class.getResource(Main.RESOURCES_ROOT + "/fxml/" + fileName).toURI());

            // If current file-object is real file, and ends with ".fxml"...
            if (currentFile.isFile() && fileName.endsWith(".fxml")) {
                // Adding its data
                loadedScenes.put(fileName,
                        SimpleFileIO.load(Main.class.getResource(Main.RESOURCES_ROOT + "/fxml/" + fileName).toURI().getPath()));
            }
        }
    }
}