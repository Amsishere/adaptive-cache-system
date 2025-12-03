package com.adaptivecache.visualizer;

import com.adaptivecache.core.*;
import com.adaptivecache.analyzer.*;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.util.*;

public class CacheSimulatorUI extends Application {
    
    private SelfOrganizingList<Integer> currentList;
    private TextArea statusArea;
    private TextArea operationsArea;
    private BarChart<String, Number> hitRateChart;
    private LineChart<Number, Number> accessCostChart;
    private ComboBox<String> strategySelector;
    private ComboBox<String> patternSelector;
    private TextField listSizeField;
    private TextField cacheSizeField;
    private TextField operationsField;
    
    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f8f9fa;");
        
        // Top: Header
        VBox header = createHeader();
        root.setTop(header);
        
        // Center: Main content with tabs
        TabPane centerTabs = createCenterTabs();
        root.setCenter(centerTabs);
        
        // Left: Controls
        VBox controlPanel = createControlPanel();
        root.setLeft(controlPanel);
        
        // Right: Real-time visualization
        VBox visualizationPanel = createVisualizationPanel();
        root.setRight(visualizationPanel);
        
        Scene scene = new Scene(root, 1400, 800);
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        
        primaryStage.setTitle("Adaptive Cache Simulator");
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // Initialize with default list
        initializeDefaultList();
    }
    
    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(20));
        header.setStyle("-fx-background-color: #2c3e50;");
        
        Label title = new Label("Adaptive Cache Optimization Simulator");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 28px; -fx-font-weight: bold;");
        
        Label subtitle = new Label("Compare self-organizing list strategies in real-time");
        subtitle.setStyle("-fx-text-fill: #bdc3c7; -fx-font-size: 14px;");
        
        header.getChildren().addAll(title, subtitle);
        return header;
    }
    
   
