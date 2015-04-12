package kouyang.irlsubs;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.PipedInputStream;

import javafx.animation.PathTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.HLineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import kouyang.irlsubs.audio.MainAudio;
import kouyang.irlsubs.audio.OffsetCalc;
import kouyang.irlsubs.audio.SpeechRec;

import javafx.geometry.Insets;

import com.github.sarxos.webcam.Webcam;

public class WebCamAppLauncher extends Application {

	/** Dimensions of the webcam. */
	private static final int SCALE = 30;
	private static final Dimension WEBCAM_DIMENSIONS = new Dimension(SCALE * 16, SCALE * 9);

	// has a webcam been selected?
	private boolean webcamSelected = false;

	private StringProperty subtitleProp;
	private DoubleProperty angleProp;
	
	// Audio CLasses
	private MainAudio mainAudio;
	private OffsetCalc offsetCalc;
	private SpeechRec speechRec;

	private class WebCamInfo {

		private String webCamName;
		private int webCamIndex;

		public String getWebCamName() {
			return webCamName;
		}

		public void setWebCamName(String webCamName) {
			this.webCamName = webCamName;
		}

		public int getWebCamIndex() {
			return webCamIndex;
		}

		public void setWebCamIndex(int webCamIndex) {
			this.webCamIndex = webCamIndex;
		}

		@Override
		public String toString() {
			return webCamName;
		}
	}

	private class Lang {
		private String name;
		private String prefix;

		public Lang(String name, String prefix) {
			this.name = name;
			this.prefix = prefix;
		}

		public String getPrefix() { return prefix; }

		@Override
		public String toString() { return name; }
	}

	private FlowPane bottomCameraControlPane;
	private FlowPane topPane;
	private BorderPane root;
	private String cameraListPromptText = "Choose Camera";
	private String langListPromptText = "Choose Language";
	private ImageView imgWebCamCapturedImage1, imgWebCamCapturedImage2;
	private Text text1, text2;
	private Webcam webCam = null;
	private boolean stopCamera = false;
	private BufferedImage grabbedImage;
	private ObjectProperty<Image> imageProperty = new SimpleObjectProperty<Image>();
	private GridPane viewport;
	private Button btnCamreaStop;
	private Button btnCamreaStart;
	private Button btnCameraDispose;
	
	final Circle dot1 = new Circle(10);
	final Circle dot2 = new Circle(10);

	private static final double SPACING = 20;
	
	@Override
	public void start(final Stage primaryStage) {
		primaryStage
				.setTitle("IRL Subs!");

		root = new BorderPane();
		topPane = new FlowPane();
		topPane.setAlignment(Pos.CENTER);
		topPane.setHgap(20);
		topPane.setOrientation(Orientation.HORIZONTAL);
		topPane.setPrefHeight(40);
		root.setTop(topPane);
		
		viewport = new GridPane();
		viewport.setStyle("-fx-background-color: #000;");
		ColumnConstraints con1 = new ColumnConstraints();
		con1.setPercentWidth(50);
		ColumnConstraints con2 = new ColumnConstraints();
		con2.setPercentWidth(50);
		viewport.getColumnConstraints().addAll(con1, con2);
		RowConstraints row = new RowConstraints();
		row.setPercentHeight(20);
		viewport.getRowConstraints().add(row);
		
		imgWebCamCapturedImage1 = new ImageView();
		GridPane.setHalignment(imgWebCamCapturedImage1, HPos.CENTER);
		viewport.add(imgWebCamCapturedImage1, 0, 1);
		text1 = new Text();
		text1.setFont(Font.font("Verdana", FontPosture.REGULAR, 24));
		text1.setFill(Color.WHITE);
		VBox vbox1 = new VBox();
		vbox1.setPadding(new Insets(0, 0, 0, 60));
		vbox1.getChildren().add(text1);
		
//		GridPane.setHalignment(text1, HPos.CENTER);
		viewport.add(vbox1, 0, 2);
		
		subtitleProp = text1.textProperty();
		
		imgWebCamCapturedImage2 = new ImageView();
		GridPane.setHalignment(imgWebCamCapturedImage2, HPos.CENTER);
		viewport.add(imgWebCamCapturedImage2, 1, 1);
		text2 = new Text();
		text2.setFont(Font.font("Verdana", FontPosture.REGULAR, 24));
		text2.textProperty().bind(text1.textProperty());
		text2.setFill(Color.WHITE);
		VBox vbox2 = new VBox();
		vbox2.setPadding(new Insets(0, 0, 0, 60));
		vbox2.getChildren().add(text2);
		viewport.add(vbox2, 1, 2);
		root.setCenter(viewport);
		
		createTopPanel();
		bottomCameraControlPane = new FlowPane();
		bottomCameraControlPane.setOrientation(Orientation.HORIZONTAL);
		bottomCameraControlPane.setAlignment(Pos.CENTER);
		bottomCameraControlPane.setHgap(20);
		bottomCameraControlPane.setVgap(10);
		bottomCameraControlPane.setPrefHeight(40);
		bottomCameraControlPane.setDisable(true);
		createCameraControls();
		root.setBottom(bottomCameraControlPane);

		primaryStage.setScene(new Scene(root));
		primaryStage.setWidth(640);
		primaryStage.setHeight(480);
		primaryStage.centerOnScreen();
//		primaryStage.setFullScreen(true);
		
		angleProp = new SimpleDoubleProperty();
		dot1.setFill(Color.SKYBLUE);
		dot2.setFill(Color.SKYBLUE);
		viewport.add(dot1, 0, 0);
		viewport.add(dot2, 1, 0);
		dot1.setTranslateY(-5);
		dot2.setTranslateY(-5);
		GridPane.setValignment(dot1, VPos.BOTTOM);
		GridPane.setValignment(dot2, VPos.BOTTOM);
		
		
		angleProp.addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> ov,
					Number oldVal, Number newVal) {
				System.out.println(String.format("change from %f to %f", oldVal.doubleValue(), newVal.doubleValue()));
				
				double dOldVal = oldVal.doubleValue();
				double dNewVal = newVal.doubleValue();
				
				
				double width = (viewport.widthProperty().divide(2).subtract(SPACING * 2)).get();
				double d = width / 2 / Math.tan(Math.toRadians(83.0 / 2));
				double xOld = Math.tan(Math.toRadians(dOldVal)) * d;
				double xNew = Math.tan(Math.toRadians(dNewVal)) * d;
				
				System.out.println("theta is " + dNewVal + ", move to " + xNew);
				
				Path path1 = new Path();
				path1.getElements().add(new MoveTo(dot1.getTranslateX(), dot1.getTranslateY()));
				path1.getElements().add(new HLineTo(dot1.getTranslateX() - xNew));
				PathTransition trans1 = new PathTransition();
				trans1.setDuration(Duration.millis(500));
				trans1.setPath(path1);
				trans1.setNode(dot1);
				
				Path path2 = new Path();
				path2.getElements().add(new MoveTo(dot2.getTranslateX(), dot2.getTranslateY()));
				path2.getElements().add(new HLineTo(dot2.getTranslateX() - xNew));
				PathTransition trans2 = new PathTransition();
				trans2.setDuration(Duration.millis(500));
				trans2.setPath(path2);
				trans2.setNode(dot2);
				
				trans1.play();
				trans2.play();
			}
		});
		
		primaryStage.addEventHandler(KeyEvent.KEY_PRESSED,
				new EventHandler<KeyEvent>() {
					@Override
					public void handle(KeyEvent ke) {
						if (ke.getCode() == KeyCode.ESCAPE) {
							System.exit(0);
						}
						if (ke.getCode() == KeyCode.F) {
							primaryStage.setFullScreen(!primaryStage
									.isFullScreen());
						}
					}
				});

		primaryStage.addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST,
				new EventHandler<WindowEvent>() {
					@Override
					public void handle(WindowEvent we) {
						System.exit(0);
					}
				});

		primaryStage.show();

		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				setImageViewSize();
			}
		});
	}

	protected void setImageViewSize() {
		DoubleProperty width = new SimpleDoubleProperty();
		DoubleProperty height = new SimpleDoubleProperty();
		width.bind(viewport.widthProperty().divide(2).subtract(SPACING * 2));
		height.bind(width.divide(16).multiply(9));

		
		imgWebCamCapturedImage1.setPreserveRatio(true);
//		imgWebCamCapturedImage1.fitHeightProperty().bind(height);
		imgWebCamCapturedImage1.fitWidthProperty().bind(width);
		text1.wrappingWidthProperty().bind(width.subtract(60));
		

		imgWebCamCapturedImage2.setPreserveRatio(true);
//		imgWebCamCapturedImage2.fitHeightProperty().bind(height);
		imgWebCamCapturedImage2.fitWidthProperty().bind(width);
		text2.wrappingWidthProperty().bind(width.subtract(60));
		
		dot1.setTranslateX(width.get());
		dot2.setTranslateX(width.get());
		
//		dot1.setCenterX(SPACING + width.get() / 1);
//		dot2.setCenterX(3 * SPACING + width.get() / 1);
	}

	private void createTopPanel() { 

		int webCamCounter = 0;
//		Label lbInfoLabel = new Label("Select Your WebCam Camera");
		ObservableList<WebCamInfo> options = FXCollections
				.observableArrayList();

//		topPane.getChildren().add(lbInfoLabel);

		for (Webcam webcam : Webcam.getWebcams()) {
			WebCamInfo webCamInfo = new WebCamInfo();
			webCamInfo.setWebCamIndex(webCamCounter);
			webCamInfo.setWebCamName(webcam.getName());
			options.add(webCamInfo);
			webCamCounter++;
		}

		ComboBox<WebCamInfo> cameraOptions = new ComboBox<WebCamInfo>();
		cameraOptions.setItems(options);
		cameraOptions.setPromptText(cameraListPromptText);
		cameraOptions.getSelectionModel().selectedItemProperty()
				.addListener(new ChangeListener<WebCamInfo>() {

					@Override
					public void changed(
							ObservableValue<? extends WebCamInfo> arg0,
							WebCamInfo arg1, WebCamInfo arg2) {
						if (arg2 != null) {

							// start speech recognition
							if (!webcamSelected) {
								webcamSelected = true;
								startSpeechRecognition();
							}

							System.out.println("WebCam Index: "
									+ arg2.getWebCamIndex() + ": WebCam Name:"
									+ arg2.getWebCamName());
							initializeWebCam(arg2.getWebCamIndex());
						}
					}
				});
		topPane.getChildren().add(cameraOptions);

		ComboBox<Lang> langOptions = new ComboBox<Lang>();
		langOptions.setPromptText(langListPromptText);
		ObservableList<Lang> langs = FXCollections.observableArrayList();
		langs.add(new Lang("English", "en"));
		langs.add(new Lang("French", "fr"));
		langs.add(new Lang("Spanish", "es"));
		langOptions.setItems(langs);
		langOptions.getSelectionModel().selectedItemProperty()
				.addListener(new ChangeListener<Lang>() {

					@Override
					public void changed(
							ObservableValue<? extends Lang> arg0,
							Lang arg1, Lang arg2) {
						if (arg2 != null) {
//							System.out.println(arg2.getPrefix());
							if (speechRec != null)
								speechRec.setLanguage(arg2.getPrefix());
						}
					}
				});
		topPane.getChildren().add(langOptions);
	}

	protected void initializeWebCam(final int webCamIndex) {
		Task<Void> webCamTask = new Task<Void>() {

			@Override
			protected Void call() throws Exception {
				if (webCam != null) {
					disposeWebCamCamera();
				}

				webCam = Webcam.getWebcams().get(webCamIndex);
				webCam.setCustomViewSizes(new Dimension[] { WEBCAM_DIMENSIONS });
				webCam.setViewSize(WEBCAM_DIMENSIONS);
				webCam.open();

				startWebCamStream();
				return null;
			}
		};

		Thread webCamThread = new Thread(webCamTask);
		webCamThread.setDaemon(true);
		webCamThread.start();

		bottomCameraControlPane.setDisable(false);
		btnCamreaStart.setDisable(true);
	}

	protected void startWebCamStream() {
		stopCamera = false;
		Task<Void> task = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				while (!stopCamera) {
					try {
						if ((grabbedImage = webCam.getImage()) != null) {
							Platform.runLater(new Runnable() {
								@Override
								public void run() {
									Image mainiamge = SwingFXUtils.toFXImage(
											grabbedImage, null);
									imageProperty.set(mainiamge);
								}
							});
							grabbedImage.flush();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				return null;
			}
		};

		Thread th = new Thread(task);
		th.setDaemon(true);
		th.start();
		imgWebCamCapturedImage1.imageProperty().bind(imageProperty);
		imgWebCamCapturedImage2.imageProperty().bind(imageProperty);
	}

	private void createCameraControls() {
		btnCamreaStop = new Button();
		btnCamreaStop.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				stopWebCamCamera();
			}
		});
		btnCamreaStop.setText("Stop Camera");
		btnCamreaStart = new Button();
		btnCamreaStart.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				startWebCamCamera();
			}
		});
		btnCamreaStart.setText("Start Camera");
		btnCameraDispose = new Button();
		btnCameraDispose.setText("Dispose Camera");
		btnCameraDispose.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				disposeWebCamCamera();
			}
		});
		bottomCameraControlPane.getChildren().add(btnCamreaStart);
		bottomCameraControlPane.getChildren().add(btnCamreaStop);
		bottomCameraControlPane.getChildren().add(btnCameraDispose);
	}

	protected void disposeWebCamCamera() {
		stopCamera = true;
		webCam.close();
		btnCamreaStart.setDisable(true);
		btnCamreaStop.setDisable(true);
	}

	protected void startWebCamCamera() {
		stopCamera = false;
		startWebCamStream();
		btnCamreaStop.setDisable(false);
		btnCamreaStart.setDisable(true);
	}

	protected void stopWebCamCamera() {
		stopCamera = true;
		btnCamreaStart.setDisable(false);
		btnCamreaStop.setDisable(true);
	}

	public static void main(String[] args) {
		launch(args);
	}

	private void startSpeechRecognition() {
		// Initialize 2 Streams
		mainAudio = new MainAudio(2);

		PipedInputStream[] streams = mainAudio.initialize();

		offsetCalc = new OffsetCalc(0.0762, 1.0, streams[0], angleProp);
		speechRec = new SpeechRec(5.0, streams[1], subtitleProp);
//
		mainAudio.start();
		offsetCalc.start();
		speechRec.start();
	}
}
