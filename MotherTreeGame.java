import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class MotherTreeGame extends Application {

    // --- ENUMS ---
    public enum Role {
        HARVESTER("Harvester", "🌿", Color.GREENYELLOW),
        PROTECTOR("Protector", "🛡️", Color.DODGERBLUE),
        MYSTIC("Mystic", "🔮", Color.MEDIUMPURPLE),
        CATALYST("Catalyst", "⚡", Color.GOLD),
        SENTINEL("Sentinel", "👁️", Color.CRIMSON);

        public final String name;
        public final String icon;
        public final Color color;

        Role(String name, String icon, Color color) {
            this.name = name;
            this.icon = icon;
            this.color = color;
        }
    }

    public enum EnemyType {
        SPORE("Blight Spore", "🩸", 30.0, 4.0, 0.8),
        PARASITE("Decay Parasite", "🦟", 60.0, 2.0, 0.5),
        MOLD("Corruption Mold", "🍄", 120.0, 5.0, 0.3),
        WEAVER("Rot Weaver", "ps", 90.0, 8.0, 0.6),
        SOVEREIGN("Void Sovereign", "☣️", 500.0, 15.0, 0.2);

        public final String name;
        public final String icon;
        public final double maxHp;
        public final double dps;
        public final double speed;

        EnemyType(String name, String icon, double maxHp, double dps, double speed) {
            this.name = name;
            this.icon = icon;
            this.maxHp = maxHp;
            this.dps = dps;
            this.speed = speed;
        }
    }

    // --- DATA STRUCTURES ---
    public static class TreeNode {
        public static int idCounter = 1;
        public final int id;
        public final int parentId; // -1 for Root
        public final int generation;
        public Role role;
        public int tier = 1; // 1 to 5
        public double hp = 100.0;
        public double maxHp = 100.0;
        public double x, y;
        public double targetX, targetY;
        public boolean isCorrupted = false;
        public double corruptionLevel = 0.0;

        public TreeNode(int parentId, int generation, Role role, double x, double y) {
            this.id = idCounter++;
            this.parentId = parentId;
            this.generation = generation;
            this.role = role;
            this.x = x;
            this.y = y;
            this.targetX = x;
            this.targetY = y;
        }

        public double getResourceYield() {
            if (role != Role.HARVESTER) return 0.0;
            return 2.5 * tier * generation;
        }

        public double getHealPower() {
            if (role != Role.MYSTIC) return 0.0;
            return 1.5 * tier;
        }
    }

    public static class Enemy {
        public EnemyType type;
        public double hp;
        public double x, y;
        public int targetNodeId;

        public Enemy(EnemyType type, double x, double y, int targetNodeId) {
            this.type = type;
            this.hp = type.maxHp;
            this.x = x;
            this.y = y;
            this.targetNodeId = targetNodeId;
        }
    }

    // --- GAME STATE ---
    private double solarEssence = 200.0;
    private double concentratedSap = 0.0;
    private double ancientAmber = 0.0;
    private double totalEnergyAccumulated = 0.0;
    private double coreHp = 1000.0;
    private double maxCoreHp = 1000.0;

    private int griefPoints = 0;
    private double necroticShockTimer = 0.0;
    private int waveCount = 0;
    private double waveTimer = 30.0;

    private TreeNode rootNode;
    private final List<TreeNode> nodes = new CopyOnWriteArrayList<>();
    private final List<Enemy> enemies = new CopyOnWriteArrayList<>();
    private TreeNode selectedNode = null;

    // Upgrades / Amber Perks
    private int amberRootsLvl = 0;
    private int sapAlchemistLvl = 0;

    // UI Controls
    private Canvas canvas;
    private Label essenceLabel, sapLabel, amberLabel, griefLabel, bloomProgressLabel, coreHpLabel, waveLabel;
    private VBox inspectorBox;
    private Button sproutBtn, evolveBtn, pruneBtn;
    private Label inspectorTitle, inspectorStats;

    private boolean isGameOver = false;
    private boolean isVictory = false;

    // --- MAIN JAVAFX LAUNCH ---
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Mother Tree: Lineage of Life (Java Engine)");

        // Initialize Root Node
        rootNode = new TreeNode(-1, 0, Role.PROTECTOR, 400, 300);
        rootNode.maxHp = 1000.0;
        rootNode.hp = 1000.0;
        nodes.add(rootNode);

        // Layout Setup
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #050b14;");

        // Top Dashboard HUD
        HBox hud = createHUD();
        root.setTop(hud);

        // Center SVG/Canvas Tree Visualizer
        canvas = new Canvas(800, 600);
        canvas.setOnMouseClicked(e -> handleCanvasClick(e.getX(), e.getY()));
        Pane canvasContainer = new Pane(canvas);
        canvasContainer.setStyle("-fx-background-color: #03070d;");
        canvas.widthProperty().bind(canvasContainer.widthProperty());
        canvas.heightProperty().bind(canvasContainer.heightProperty());
        root.setCenter(canvasContainer);

        // Right Inspector Panel (CRUD Controls)
        VBox rightPanel = createInspectorPanel();
        root.setRight(rightPanel);

        // Game Loop (60 FPS Animation Timer)
        AnimationTimer timer = new AnimationTimer() {
            private long lastNano = 0;

            @Override
            public void handle(long now) {
                if (lastNano == 0) {
                    lastNano = now;
                    return;
                }
                double dt = (now - lastNano) / 1e9;
                lastNano = now;

                if (!isGameOver) {
                    updateGame(dt);
                    renderCanvas();
                    updateUI();
                }
            }
        };
        timer.start();

        Scene scene = new Scene(root, 1100, 700);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // --- HUD SETUP ---
    private HBox createHUD() {
        HBox hud = new HBox(15);
        hud.setPadding(new Insets(12));
        hud.setAlignment(Pos.CENTER_LEFT);
        hud.setStyle("-fx-background-color: #0a1322; -fx-border-color: #1e2d4a; -fx-border-width: 0 0 2 0;");

        essenceLabel = createStyledLabel("☀️ Essence: 200", Color.GOLD);
        sapLabel = createStyledLabel("🧪 Sap: 0", Color.LIMEGREEN);
        amberLabel = createStyledLabel("💎 Amber: 0", Color.ORANGE);
        griefLabel = createStyledLabel("💀 Grief: 0/35", Color.INDIANRED);
        coreHpLabel = createStyledLabel("❤️ Core: 1000/1000", Color.RED);
        bloomProgressLabel = createStyledLabel("🌸 Bloom: 0%", Color.CYAN);
        waveLabel = createStyledLabel("🌊 Wave in: 30s", Color.LIGHTSKYBLUE);

        hud.getChildren().addAll(essenceLabel, sapLabel, amberLabel, griefLabel, coreHpLabel, bloomProgressLabel, waveLabel);
        return hud;
    }

    private Label createStyledLabel(String text, Color color) {
        Label label = new Label(text);
        label.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        label.setTextFill(color);
        return label;
    }

    // --- RIGHT CONTROL PANEL ---
    private VBox createInspectorPanel() {
        VBox panel = new VBox(12);
        panel.setPrefWidth(280);
        panel.setPadding(new Insets(15));
        panel.setStyle("-fx-background-color: #09121f; -fx-border-color: #1a2a40; -fx-border-width: 0 0 0 2;");

        Label title = new Label("LINEAGE INSPECTOR");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        title.setTextFill(Color.CYAN);

        inspectorTitle = new Label("Select a Node");
        inspectorTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        inspectorTitle.setTextFill(Color.WHITE);

        inspectorStats = new Label("Click any node on the tree canvas to view stats and execute CRUD operations.");
        inspectorStats.setWrapText(true);
        inspectorStats.setTextFill(Color.LIGHTGRAY);

        sproutBtn = new Button("🌱 Sprout Offspring (100 Essence)");
        styleButton(sproutBtn, "#10b981");
        sproutBtn.setOnAction(e -> showSproutMenu());

        evolveBtn = new Button("⚡ Nurture / Evolve (500 Essence)");
        styleButton(evolveBtn, "#3b82f6");
        evolveBtn.setOnAction(e -> evolveSelectedNode());

        pruneBtn = new Button("✂️ Prune Node (Gain Sap)");
        styleButton(pruneBtn, "#ef4444");
        pruneBtn.setOnAction(e -> pruneSelectedNode());

        sproutBtn.setDisable(true);
        evolveBtn.setDisable(true);
        pruneBtn.setDisable(true);

        // Ascension Reset Button
        Button ascendBtn = new Button("✨ Ascend Mother Tree");
        styleButton(ascendBtn, "#8b5cf6");
        ascendBtn.setOnAction(e -> triggerAscension());

        panel.getChildren().addAll(title, new Separator(), inspectorTitle, inspectorStats, sproutBtn, evolveBtn, pruneBtn, new Separator(), ascendBtn);
        return panel;
    }

    private void styleButton(Button btn, String hexColor) {
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 12;", hexColor));
    }

    // --- GAME LOGIC UPDATE ---
    private void updateGame(double dt) {
        // Handle Necrotic Shock Penalty
        if (necroticShockTimer > 0) {
            necroticShockTimer -= dt;
        } else {
            // Income Generation
            double currentIncome = 0.0;
            for (TreeNode node : nodes) {
                currentIncome += node.getResourceYield();
            }
            double incomeTick = currentIncome * dt;
            solarEssence += incomeTick;
            totalEnergyAccumulated += incomeTick;
        }

        // Mystic Passive Healing
        for (TreeNode node : nodes) {
            if (node.role == Role.MYSTIC) {
                double healAmount = node.getHealPower() * dt * 5.0;
                for (TreeNode target : nodes) {
                    if (target.id == node.parentId || target.parentId == node.id) {
                        target.hp = Math.min(target.maxHp, target.hp + healAmount);
                    }
                }
            }
        }

        // Wave Spawning System
        waveTimer -= dt;
        if (waveTimer <= 0) {
            spawnBlightWave();
            waveTimer = 30.0;
        }

        // Enemy Combat & Movement
        for (Enemy enemy : enemies) {
            TreeNode target = getNodeById(enemy.targetNodeId);
            if (target == null) target = rootNode;

            double dx = target.x - enemy.x;
            double dy = target.y - enemy.y;
            double dist = Math.hypot(dx, dy);

            if (dist > 5.0) {
                enemy.x += (dx / dist) * enemy.type.speed * dt * 60.0;
                enemy.y += (dy / dist) * enemy.type.speed * dt * 60.0;
            } else {
                // Attack Target Node
                target.hp -= enemy.type.dps * dt;
                if (target == rootNode) {
                    coreHp = Math.max(0, rootNode.hp);
                }

                // Node Destroyed by Enemies
                if (target.hp <= 0 && target != rootNode) {
                    nodes.remove(target);
                    enemies.remove(enemy);
                    if (selectedNode == target) selectedNode = null;
                }
            }
        }

        // Check Loss Conditions
        checkLossConditions();

        // Check Win Condition
        checkWinCondition();
    }

    // --- CRUD OPERATIONS ---
    private void showSproutMenu() {
        if (selectedNode == null) return;

        ChoiceDialog<Role> dialog = new ChoiceDialog<>(Role.HARVESTER, Role.values());
        dialog.setTitle("Sprout New Offspring");
        dialog.setHeaderText("Choose a specialized class for Generation " + (selectedNode.generation + 1));
        dialog.setContentText("Class:");

        Optional<Role> result = dialog.showAndWait();
        result.ifPresent(role -> {
            double cost = 100.0 * Math.pow(1.3, selectedNode.generation);
            if (solarEssence >= cost) {
                solarEssence -= cost;
                double angle = Math.random() * Math.PI * 2;
                double spawnX = selectedNode.x + Math.cos(angle) * 80.0;
                double spawnY = selectedNode.y + Math.sin(angle) * 80.0;

                TreeNode child = new TreeNode(selectedNode.id, selectedNode.generation + 1, role, spawnX, spawnY);
                nodes.add(child);
            }
        });
    }

    private void evolveSelectedNode() {
        if (selectedNode == null) return;
        double cost = 500.0 * selectedNode.tier;
        if (solarEssence >= cost && selectedNode.tier < 5) {
            solarEssence -= cost;
            selectedNode.tier++;
            selectedNode.maxHp += 50.0;
            selectedNode.hp = selectedNode.maxHp;
            updateInspector();
        }
    }

    private void pruneSelectedNode() {
        if (selectedNode == null || selectedNode == rootNode) return;

        // Apply Generational Grief Points
        int pointsAdded = 1;
        if (selectedNode.generation >= 3) pointsAdded = 3;
        if (selectedNode.tier >= 4) pointsAdded = 10;

        griefPoints += pointsAdded;
        if (griefPoints >= 25 && griefPoints < 35) {
            necroticShockTimer = 30.0; // Trigger Necrotic Shock
        }

        // Harvest Sap Reward
        double sapEarned = 10.0 * selectedNode.tier * (1.0 + sapAlchemistLvl * 0.2);
        concentratedSap += sapEarned;

        // Remove Node and Children
        removeNodeAndDescendants(selectedNode);
        selectedNode = null;
        updateInspector();
    }

    private void removeNodeAndDescendants(TreeNode parent) {
        nodes.remove(parent);
        for (TreeNode child : nodes) {
            if (child.parentId == parent.id) {
                removeNodeAndDescendants(child);
            }
        }
    }

    // --- ENEMY WAVES & COMBAT ---
    private void spawnBlightWave() {
        waveCount++;
        int enemyCount = 2 + waveCount;
        EnemyType type = EnemyType.values()[Math.min(waveCount % EnemyType.values().length, EnemyType.values().length - 1)];

        for (int i = 0; i < enemyCount; i++) {
            double spawnAngle = Math.random() * Math.PI * 2;
            double spawnDist = 400.0;
            double spawnX = rootNode.x + Math.cos(spawnAngle) * spawnDist;
            double spawnY = rootNode.y + Math.sin(spawnAngle) * spawnDist;

            int targetId = rootNode.id;
            if (!nodes.isEmpty()) {
                targetId = nodes.get((int) (Math.random() * nodes.size())).id;
            }
            enemies.add(new Enemy(type, spawnX, spawnY, targetId));
        }
    }

    // --- CHECK GAME CONDITIONS ---
    private void checkLossConditions() {
        // Loss 1: Grief Threshold Reached (35 points)
        if (griefPoints >= 35) {
            triggerGameOver("The lineage has been butchered; the Mother Tree dies childless. (Generational Collapse)");
        }
        // Loss 2: Root Core Destroyed
        if (rootNode.hp <= 0) {
            triggerGameOver("The corruption has reached the heart. The Mother Tree withers into ash. (Root Core Corruption)");
        }
        // Loss 3: Canopy Extinction
        if (nodes.size() == 1 && solarEssence < 100.0 && getHarvesterCount() == 0) {
            triggerGameOver("The canopy is barren. Alone and cold, the Mother Tree fades into eternal sleep. (Canopy Extinction)");
        }
    }

    private void checkWinCondition() {
        if (isVictory) return;

        int maxGen = nodes.stream().mapToInt(n -> n.generation).max().orElse(0);
        boolean hasParagon = nodes.stream().anyMatch(n -> n.tier == 5);

        if (maxGen >= 10 && totalEnergyAccumulated >= 1000000.0 && hasParagon) {
            isVictory = true;
            ancientAmber += 50.0;
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("THE GREAT BLOOM ACHIEVED!");
            alert.setHeaderText("Victory! Your Lineage Reaches Perfection!");
            alert.setContentText("You reached Generation 10, accumulated over 1,000,000 Energy, and cultivated an Ancient Paragon. Earned +50 Ancient Amber!");
            alert.showAndWait();
        }
    }

    private void triggerGameOver(String reason) {
        isGameOver = true;
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("GAME OVER");
        alert.setHeaderText("The Lineage Has Fallen");
        alert.setContentText(reason);
        alert.showAndWait();
    }

    private void triggerAscension() {
        ancientAmber += (nodes.size() * 2) + (waveCount * 5);
        solarEssence = 200.0 + (amberRootsLvl * 500.0);
        griefPoints = 0;
        necroticShockTimer = 0.0;
        waveCount = 0;
        waveTimer = 30.0;
        nodes.clear();
        enemies.clear();

        rootNode = new TreeNode(-1, 0, Role.PROTECTOR, canvas.getWidth() / 2, canvas.getHeight() / 2);
        rootNode.maxHp = 1000.0;
        rootNode.hp = 1000.0;
        nodes.add(rootNode);

        isGameOver = false;
        selectedNode = null;
        updateInspector();
    }

    // --- CANVAS RENDERING ---
    private void renderCanvas() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Draw Links
        gc.setLineWidth(2.0);
        for (TreeNode node : nodes) {
            if (node.parentId != -1) {
                TreeNode parent = getNodeById(node.parentId);
                if (parent != null) {
                    gc.setStroke(Color.rgb(16, 185, 129, 0.4));
                    gc.strokeLine(parent.x, parent.y, node.x, node.y);
                }
            }
        }

        // Draw Nodes
        for (TreeNode node : nodes) {
            double radius = 15.0 + (node.tier * 3.0);
            if (node == rootNode) radius = 30.0;

            // Draw Node Glow
            RadialGradient grad = new RadialGradient(0, 0, node.x, node.y, radius, false, CycleMethod.NO_CYCLE,
                    new Stop(0, node.role.color), new Stop(1, Color.TRANSPARENT));
            gc.setFill(grad);
            gc.fillOval(node.x - radius * 1.5, node.y - radius * 1.5, radius * 3.0, radius * 3.0);

            // Draw Main Node Circle
            gc.setFill(node.role.color);
            gc.fillOval(node.x - radius, node.y - radius, radius * 2.0, radius * 2.0);

            if (node == selectedNode) {
                gc.setStroke(Color.WHITE);
                gc.setLineWidth(3.0);
                gc.strokeOval(node.x - radius - 3, node.y - radius - 3, radius * 2 + 6, radius * 2 + 6);
            }

            // Health Bar
            double hpPercent = Math.max(0, node.hp / node.maxHp);
            gc.setFill(Color.RED);
            gc.fillRect(node.x - radius, node.y + radius + 4, radius * 2, 4);
            gc.setFill(Color.LIME);
            gc.fillRect(node.x - radius, node.y + radius + 4, radius * 2 * hpPercent, 4);
        }

        // Draw Enemies
        for (Enemy enemy : enemies) {
            gc.setFill(Color.RED);
            gc.fillOval(enemy.x - 8, enemy.y - 8, 16, 16);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Segoe UI", 10));
            gc.fillText(enemy.type.icon, enemy.x - 5, enemy.y + 4);
        }
    }

    private void handleCanvasClick(double x, double y) {
        // Direct Click Enemies to Purge
        for (Enemy enemy : enemies) {
            if (Math.hypot(enemy.x - x, enemy.y - y) < 15.0) {
                enemy.hp -= 50.0;
                if (enemy.hp <= 0) enemies.remove(enemy);
                return;
            }
        }

        // Select Nodes
        selectedNode = null;
        for (TreeNode node : nodes) {
            double radius = 15.0 + (node.tier * 3.0);
            if (Math.hypot(node.x - x, node.y - y) <= radius) {
                selectedNode = node;
                break;
            }
        }
        updateInspector();
    }

    private void updateUI() {
        essenceLabel.setText(String.format("☀️ Essence: %.0f", solarEssence));
        sapLabel.setText(String.format("🧪 Sap: %.0f", concentratedSap));
        amberLabel.setText(String.format("💎 Amber: %.0f", ancientAmber));
        griefLabel.setText(String.format("💀 Grief: %d/35 %s", griefPoints, necroticShockTimer > 0 ? "(SHOCK!)" : ""));
        coreHpLabel.setText(String.format("❤️ Core: %.0f/%.0f", rootNode.hp, rootNode.maxHp));
        double progress = Math.min(100.0, (totalEnergyAccumulated / 1000000.0) * 100.0);
        bloomProgressLabel.setText(String.format("🌸 Bloom: %.1f%%", progress));
        waveLabel.setText(String.format("🌊 Wave in: %.0fs", waveTimer));
    }

    private void updateInspector() {
        if (selectedNode == null) {
            inspectorTitle.setText("Select a Node");
            inspectorStats.setText("Click any node on the tree canvas to view stats and execute CRUD operations.");
            sproutBtn.setDisable(true);
            evolveBtn.setDisable(true);
            pruneBtn.setDisable(true);
        } else {
            inspectorTitle.setText(selectedNode.role.icon + " " + selectedNode.role.name + " (Gen " + selectedNode.generation + ")");
            inspectorStats.setText(String.format("Level/Tier: %d/5\nHP: %.0f/%.0f\nResource Yield: %.1f/s\nPosition: (%.0f, %.0f)",
                    selectedNode.tier, selectedNode.hp, selectedNode.maxHp, selectedNode.getResourceYield(), selectedNode.x, selectedNode.y));

            sproutBtn.setDisable(false);
            evolveBtn.setDisable(selectedNode.tier >= 5);
            pruneBtn.setDisable(selectedNode == rootNode);
        }
    }

    // --- HELPER UTILS ---
    private TreeNode getNodeById(int id) {
        for (TreeNode n : nodes) {
            if (n.id == id) return n;
        }
        return null;
    }

    private int getHarvesterCount() {
        int count = 0;
        for (TreeNode n : nodes) {
            if (n.role == Role.HARVESTER) count++;
        }
        return count;
    }
}