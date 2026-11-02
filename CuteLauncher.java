import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

public class CuteLauncher extends JFrame {
	private static final Color FALLBACK_BG = new Color(255, 246, 250);
	private Color ink = new Color(60, 40, 70);
	private Color[] cardColors = {
		new Color(255, 229, 235), new Color(231, 244, 255),
		new Color(239, 233, 255), new Color(232, 249, 235),
		new Color(255, 242, 216)
	};

	private final Path dataDir = Paths.get(System.getProperty("user.home"), ".cute-launcher");
	private final Path libraryFile = dataDir.resolve("library.properties");
	private final List<Game> games = new ArrayList<>();
	private final List<Game> filtered = new ArrayList<>();
	private final JPanel grid = new JPanel();
	private final JTextField searchField = new JTextField();
	private final JSpinner columnSpinner = new JSpinner(new SpinnerNumberModel(3, 2, 6, 1));

	private Image backgroundImage;
	private Path backgroundPath;
	private int columns = 3;
	private String currentFilter = "All";

	private final List<Sakura> petals = new ArrayList<>();
	private final List<TrailPoint> trail = new ArrayList<>();
	private final Random rand = new Random();
	private javax.swing.Timer effectTimer;
	private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");

	public CuteLauncher() {
		super("♡ Cute Game Shelf ♡");
		loadLibrary();
		loadBackground();

		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setMinimumSize(new Dimension(800, 540));
		setSize(1020, 700);
		setLocationRelativeTo(null);

		LoadingPanel loading = new LoadingPanel();
		setContentPane(loading);
		setVisible(true);

		javax.swing.Timer switchTimer = new javax.swing.Timer(1300, e -> {
			((javax.swing.Timer) e.getSource()).stop();
			showMainUI();
		});
		switchTimer.setRepeats(false);
		switchTimer.start();
	}

	private void showMainUI() {
		BackgroundPanel content = new BackgroundPanel();
		content.setLayout(new BorderLayout(10, 8));
		setContentPane(content);

		Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
			if (event instanceof MouseEvent) {
				MouseEvent me = (MouseEvent) event;
				if (me.getID() == MouseEvent.MOUSE_MOVED || me.getID() == MouseEvent.MOUSE_DRAGGED) {
					Point p = me.getLocationOnScreen();
					SwingUtilities.convertPointFromScreen(p, content);
					if (content.contains(p)) {
						trail.add(0, new TrailPoint(p.x, p.y));
						if (trail.size() > 26) trail.remove(trail.size() - 1);
						if (rand.nextInt(7) == 0) {
							trail.add(0, new TrailPoint(p.x + rand.nextInt(13) - 6, p.y + rand.nextInt(13) - 6, true));
						}
					}
				}
			}
		}, AWTEvent.MOUSE_MOTION_EVENT_MASK);

		JPanel header = new JPanel(new BorderLayout(10, 6));
		header.setOpaque(false);
		header.setBorder(new EmptyBorder(16, 18, 4, 18));

		JLabel title = new JLabel("♡ My Game Shelf ♡");
		title.setFont(new Font("Monospaced", Font.BOLD, 22));
		title.setForeground(ink);
		header.add(title, BorderLayout.WEST);

		JPanel topControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		topControls.setOpaque(false);

		searchField.setColumns(12);
		searchField.setFont(new Font("Monospaced", Font.PLAIN, 13));
		searchField.setForeground(ink);
		searchField.setBackground(Color.WHITE);
		searchField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(220, 180, 200), 2),
			new EmptyBorder(4, 6, 4, 6)));
		searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
			public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
			public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
		});

		JButton add = makeCuteButton("＋ Import");
		add.addActionListener(e -> importGame());
		JButton bgButton = makeCuteButton("Background");
		bgButton.addActionListener(e -> chooseBackground());

		JLabel colLabel = new JLabel("Cols");
		colLabel.setForeground(ink);
		colLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
		columnSpinner.addChangeListener(e -> {
			columns = (Integer) columnSpinner.getValue();
			saveLibrary();
			refreshGames();
		});

		topControls.add(searchField);
		topControls.add(colLabel);
		topControls.add(columnSpinner);
		topControls.add(add);
		topControls.add(bgButton);
		header.add(topControls, BorderLayout.EAST);

		JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		filters.setOpaque(false);
		filters.setBorder(new EmptyBorder(0, 18, 6, 18));
		String[] filterNames = {"All", "Favorites", "Recently Added", "Recently Played", "Most Played"};
		for (String name : filterNames) {
			JButton b = makeCuteButton(name);
			b.addActionListener(e -> {
				currentFilter = name;
				applyFilter();
			});
			filters.add(b);
		}

		JPanel north = new JPanel(new BorderLayout());
		north.setOpaque(false);
		north.add(header, BorderLayout.NORTH);
		north.add(filters, BorderLayout.SOUTH);
		content.add(north, BorderLayout.NORTH);

		grid.setOpaque(false);
		grid.setBorder(new EmptyBorder(8, 18, 16, 18));
		JScrollPane scroll = new JScrollPane(grid);
		scroll.setBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		content.add(scroll, BorderLayout.CENTER);

		startEffects();
		applyFilter();
		revalidate();
		repaint();
	}

	private JButton makeCuteButton(String text) {
		JButton b = new JButton(text);
		b.setFont(new Font("Monospaced", Font.BOLD, 12));
		b.setForeground(ink);
		b.setBackground(new Color(255, 245, 248));
		b.setFocusPainted(false);
		b.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(255, 160, 190), 2, true),
			new EmptyBorder(5, 10, 5, 10)));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return b;
	}

	private void startEffects() {
		for (int i = 0; i < 9; i++) petals.add(new Sakura());
		effectTimer = new javax.swing.Timer(35, e -> {
			for (Sakura s : petals) s.update();
			for (int i = trail.size() - 1; i >= 0; i--) {
				trail.get(i).life--;
				if (trail.get(i).life <= 0) trail.remove(i);
			}
			repaint();
		});
		effectTimer.start();
	}

	private Image scaleIcon(Image src, int maxW, int maxH) {
		if (src == null) return null;
		int w = src.getWidth(null);
		int h = src.getHeight(null);
		if (w <= 0 || h <= 0) return src;
		double scale = Math.min((double) maxW / w, (double) maxH / h);
		int nw = Math.max(1, (int) (w * scale));
		int nh = Math.max(1, (int) (h * scale));
		BufferedImage out = new BufferedImage(maxW, maxH, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(src, (maxW - nw) / 2, (maxH - nh) / 2, nw, nh, null);
		g.dispose();
		return out;
	}

	private String findDefaultCover(Path gameFile) {
		if (gameFile == null || gameFile.getParent() == null) return null;
		Path dir = gameFile.getParent();
		String base = gameFile.getFileName().toString().replaceFirst("\\.[^.]+$", "");
		String[] candidates = {
			"cover.png", "cover.jpg", "cover.jpeg", "cover.webp",
			"icon.png", "icon.jpg", "header.png", "header.jpg",
			"capsule.png", "capsule.jpg", "poster.png", "poster.jpg",
			base + ".png", base + ".jpg", base + ".jpeg"
		};
		for (String name : candidates) {
			Path p = dir.resolve(name);
			if (Files.isRegularFile(p)) return p.toAbsolutePath().toString();
		}
		return null;
	}

	private void importGame() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
		File selected = chooser.getSelectedFile();
		String path = selected.toPath().toAbsolutePath().normalize().toString();
		for (Game game : games) {
			if (game.path.equals(path)) {
				JOptionPane.showMessageDialog(this, "Already on the shelf.");
				return;
			}
		}
		String name = selected.getName();
		int dot = name.lastIndexOf('.');
		if (dot > 0) name = name.substring(0, dot);
		name = JOptionPane.showInputDialog(this, "Name:", name);
		if (name == null || name.trim().isEmpty()) return;
		Game g = new Game(UUID.randomUUID().toString(), name.trim(), path, false, System.currentTimeMillis());
		g.iconPath = findDefaultCover(selected.toPath());
		games.add(g);
		saveLibrary();
		applyFilter();
	}

	private void chooseBackground() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "gif", "webp"));
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
		Path selected = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
		try {
			backgroundImage = new ImageIcon(selected.toString()).getImage();
			backgroundPath = selected;
			saveLibrary();
			repaint();
		} catch (Exception ex) {
			showError("Couldn't load image.");
		}
	}

	private void setGameIcon(Game game) {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Choose cover for " + game.name);
		chooser.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "webp"));
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
		game.iconPath = chooser.getSelectedFile().toPath().toAbsolutePath().normalize().toString();
		saveLibrary();
		refreshGames();
	}

	private void showDetails(Game game) {
		JDialog d = new JDialog(this, game.name, true);
		d.setLayout(new BorderLayout(12, 12));
		d.getContentPane().setBackground(new Color(255, 246, 250));

		JPanel info = new JPanel(new GridLayout(0, 1, 4, 6));
		info.setOpaque(false);
		info.setBorder(new EmptyBorder(16, 18, 8, 18));

		long size = 0;
		try { size = Files.size(Paths.get(game.path)); } catch (IOException ignored) {}
		String sizeStr = size > 0 ? String.format("%.1f MB", size / 1024.0 / 1024.0) : "unknown";

		info.add(label("Name: " + game.name));
		info.add(label("Path: " + game.path));
		info.add(label("File size: " + sizeStr));
		info.add(label("Added: " + (game.addedTime > 0 ? dateFmt.format(new Date(game.addedTime)) : "—")));
		info.add(label("Last played: " + (game.lastPlayed > 0 ? dateFmt.format(new Date(game.lastPlayed)) : "never")));
		info.add(label("Times launched: " + game.playCount));
		info.add(label("Favorite: " + (game.favorite ? "yes" : "no")));

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.setOpaque(false);
		JButton saves = makeCuteButton("Open saves");
		saves.addActionListener(e -> openSaveFolder(game));
		JButton close = makeCuteButton("Close");
		close.addActionListener(e -> d.dispose());
		buttons.add(saves);
		buttons.add(close);

		d.add(info, BorderLayout.CENTER);
		d.add(buttons, BorderLayout.SOUTH);
		d.pack();
		d.setLocationRelativeTo(this);
		d.setVisible(true);
	}

	private JLabel label(String text) {
		JLabel l = new JLabel(text);
		l.setFont(new Font("Monospaced", Font.PLAIN, 13));
		l.setForeground(ink);
		return l;
	}

	private void applyFilter() {
		String q = searchField.getText().trim().toLowerCase();
		filtered.clear();
		for (Game g : games) {
			if (!q.isEmpty() && !g.name.toLowerCase().contains(q)) continue;
			if ("Favorites".equals(currentFilter) && !g.favorite) continue;
			filtered.add(g);
		}
		filtered.sort((a, b) -> {
			if ("Recently Added".equals(currentFilter)) return Long.compare(b.addedTime, a.addedTime);
			if ("Recently Played".equals(currentFilter)) return Long.compare(b.lastPlayed, a.lastPlayed);
			if ("Most Played".equals(currentFilter)) return Integer.compare(b.playCount, a.playCount);
			if (a.favorite != b.favorite) return a.favorite ? -1 : 1;
			return a.name.compareToIgnoreCase(b.name);
		});
		refreshGames();
	}

	private void refreshGames() {
		grid.removeAll();
		if (filtered.isEmpty()) {
			grid.setLayout(new GridBagLayout());
			JLabel empty = new JLabel(games.isEmpty() ? "Shelf is empty  ♡" : "No matches");
			empty.setFont(new Font("Monospaced", Font.PLAIN, 15));
			empty.setForeground(ink);
			grid.add(empty);
		} else {
			grid.setLayout(new GridLayout(0, columns, 14, 14));
			for (int i = 0; i < filtered.size(); i++) grid.add(new GameCard(filtered.get(i), i));
		}
		grid.revalidate();
		grid.repaint();
	}

	private void launch(Game game) {
		Path file = Paths.get(game.path);
		if (!Files.isRegularFile(file)) {
			showError("File missing.");
			return;
		}
		try {
			if (isWindows()) {
				Path dir = file.getParent();
				Path bat = Files.createTempFile("cuteshelf-", ".bat");
				String content = "@echo off\r\n" +
					"cd /d \"" + dir.toAbsolutePath() + "\"\r\n" +
					"start \"\" \"" + file.getFileName() + "\"\r\n";
				Files.write(bat, content.getBytes());
				new ProcessBuilder("cmd.exe", "/c", bat.toAbsolutePath().toString())
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();
				new javax.swing.Timer(3000, ev -> {
					try { Files.deleteIfExists(bat); } catch (IOException ignored) {}
					((javax.swing.Timer) ev.getSource()).stop();
				}).start();
			} else if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(file.toFile());
			} else {
				new ProcessBuilder(file.toString()).directory(file.getParent().toFile()).start();
			}
			game.lastPlayed = System.currentTimeMillis();
			game.playCount++;
			saveLibrary();
		} catch (IOException ex) {
			showError("Launch failed: " + ex.getMessage());
		}
	}

	private void openSaveFolder(Game game) {
		try {
			Path folder = dataDir.resolve("saves").resolve(game.id);
			Files.createDirectories(folder);
			Desktop.getDesktop().open(folder.toFile());
		} catch (IOException ex) {
			showError("Can't open saves.");
		}
	}

	private void removeGame(Game game) {
		if (JOptionPane.showConfirmDialog(this, "Remove “" + game.name + "”?", "Remove", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
			games.remove(game);
			saveLibrary();
			applyFilter();
		}
	}

	private void loadLibrary() {
		try {
			if (!Files.exists(libraryFile)) return;
			Properties p = new Properties();
			try (java.io.Reader r = Files.newBufferedReader(libraryFile)) { p.load(r); }
			String cols = p.getProperty("columns");
			if (cols != null) {
				try { columns = Integer.parseInt(cols); columnSpinner.setValue(columns); } catch (Exception ignored) {}
			}
			for (String key : p.stringPropertyNames()) {
				if (!key.startsWith("game.") || !key.endsWith(".name")) continue;
				String id = key.substring(5, key.length() - 5);
				String name = p.getProperty(key);
				String path = p.getProperty("game." + id + ".path");
				boolean fav = Boolean.parseBoolean(p.getProperty("game." + id + ".favorite", "false"));
				long added = Long.parseLong(p.getProperty("game." + id + ".added", "0"));
				long played = Long.parseLong(p.getProperty("game." + id + ".lastPlayed", "0"));
				int count = Integer.parseInt(p.getProperty("game." + id + ".plays", "0"));
				String icon = p.getProperty("game." + id + ".icon");
				if (path != null) {
					Game g = new Game(id, name, path, fav, added, played, count);
					g.iconPath = icon;
					if (g.iconPath == null) g.iconPath = findDefaultCover(Paths.get(path));
					games.add(g);
				}
			}
		} catch (Exception ignored) {}
	}

	private void loadBackground() {
		try {
			if (!Files.exists(libraryFile)) return;
			Properties p = new Properties();
			try (java.io.Reader r = Files.newBufferedReader(libraryFile)) { p.load(r); }
			String path = p.getProperty("background");
			if (path != null) {
				backgroundPath = Paths.get(path);
				if (Files.isRegularFile(backgroundPath)) {
					backgroundImage = new ImageIcon(backgroundPath.toString()).getImage();
				}
			}
		} catch (IOException ignored) {}
	}

	private void saveLibrary() {
		try {
			Files.createDirectories(dataDir);
			Properties p = new Properties();
			p.setProperty("columns", String.valueOf(columns));
			for (Game game : games) {
				p.setProperty("game." + game.id + ".name", game.name);
				p.setProperty("game." + game.id + ".path", game.path);
				p.setProperty("game." + game.id + ".favorite", String.valueOf(game.favorite));
				p.setProperty("game." + game.id + ".added", String.valueOf(game.addedTime));
				p.setProperty("game." + game.id + ".lastPlayed", String.valueOf(game.lastPlayed));
				p.setProperty("game." + game.id + ".plays", String.valueOf(game.playCount));
				if (game.iconPath != null) p.setProperty("game." + game.id + ".icon", game.iconPath);
			}
			if (backgroundPath != null) p.setProperty("background", backgroundPath.toString());
			try (java.io.Writer w = Files.newBufferedWriter(libraryFile)) {
				p.store(w, "Cute Game Shelf");
			}
		} catch (IOException ex) {
			showError("Save failed.");
		}
	}

	private static String abbreviate(String text, int limit) {
		return text.length() <= limit ? text : "…" + text.substring(text.length() - limit + 1);
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase().contains("win");
	}

	private void showError(String message) {
		JOptionPane.showMessageDialog(this, message, "Oops", JOptionPane.ERROR_MESSAGE);
	}

	private static final class Game {
		final String id, name, path;
		boolean favorite;
		long addedTime, lastPlayed;
		int playCount;
		String iconPath;
		Game(String id, String name, String path, boolean favorite, long added) {
			this(id, name, path, favorite, added, 0, 0);
		}
		Game(String id, String name, String path, boolean favorite, long added, long played, int plays) {
			this.id = id; this.name = name; this.path = path; this.favorite = favorite;
			this.addedTime = added; this.lastPlayed = played; this.playCount = plays;
		}
		boolean isNew() {
			return System.currentTimeMillis() - addedTime < 24L * 60 * 60 * 1000;
		}
	}

	private static class TrailPoint {
		int x, y, life;
		boolean sparkle;
		TrailPoint(int x, int y) { this(x, y, false); }
		TrailPoint(int x, int y, boolean sparkle) {
			this.x = x; this.y = y; this.life = sparkle ? 11 : 22; this.sparkle = sparkle;
		}
	}

	private class GameCard extends JPanel {
		final Game game;
		final List<Heart> hearts = new ArrayList<>();
		final Color baseColor;
		boolean hovered = false;
		float phase = 0;

		GameCard(Game game, int index) {
			this.game = game;
			baseColor = cardColors[index % cardColors.length];
			setLayout(new BorderLayout(8, 8));
			setBackground(baseColor);
			setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(255, 200, 215), 1, true),
				new EmptyBorder(10, 10, 10, 10)));
			setOpaque(false);

			MouseAdapter hover = new MouseAdapter() {
				@Override
				public void mouseEntered(MouseEvent e) {
					hovered = true;
					repaint();
				}
				@Override
				public void mouseExited(MouseEvent e) {
					hovered = false;
					repaint();
				}
			};
			addMouseListener(hover);

			JLabel name = new JLabel((game.favorite ? "★ " : "♡ ") + game.name);
			name.setFont(new Font("Monospaced", Font.BOLD, 15));
			name.setForeground(ink);
			name.addMouseListener(hover);

			JLabel iconArea = new JLabel(game.iconPath == null ? "Set cover" : "", SwingConstants.CENTER);
			iconArea.setPreferredSize(new Dimension(220, 160));
			iconArea.setMinimumSize(new Dimension(180, 130));
			iconArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
			iconArea.setForeground(new Color(140, 100, 130));
			iconArea.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			if (game.iconPath != null) {
				try {
					Image raw = new ImageIcon(game.iconPath).getImage();
					iconArea.setIcon(new ImageIcon(scaleIcon(raw, 220, 160)));
					iconArea.setText("");
				} catch (Exception ignored) {}
			}
			iconArea.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) { setGameIcon(game); }
				@Override
				public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
				@Override
				public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
			});

			JLabel path = new JLabel(abbreviate(game.path, 32));
			path.setFont(new Font("Monospaced", Font.PLAIN, 10));
			path.setForeground(new Color(110, 85, 115));
			path.addMouseListener(hover);

			JPanel top = new JPanel(new BorderLayout(6, 8));
			top.setOpaque(false);
			top.add(name, BorderLayout.NORTH);
			top.add(iconArea, BorderLayout.CENTER);
			top.add(path, BorderLayout.SOUTH);
			top.addMouseListener(hover);

			JPanel buttons = new JPanel(new GridLayout(1, 5, 3, 0));
			buttons.setOpaque(false);
			JButton fav = makeCuteButton(game.favorite ? "Unfav" : "Fav");
			fav.addActionListener(e -> { game.favorite = !game.favorite; saveLibrary(); applyFilter(); });
			JButton play = makeCuteButton("▶ Play");
			play.addActionListener(e -> launch(game));
			JButton details = makeCuteButton("Details");
			details.addActionListener(e -> showDetails(game));
			JButton saves = makeCuteButton("Saves");
			saves.addActionListener(e -> openSaveFolder(game));
			JButton remove = makeCuteButton("X");
			remove.addActionListener(e -> removeGame(game));
			buttons.add(fav);
			buttons.add(play);
			buttons.add(details);
			buttons.add(saves);
			buttons.add(remove);
			buttons.addMouseListener(hover);

			add(top, BorderLayout.CENTER);
			add(buttons, BorderLayout.SOUTH);

			if (game.favorite) {
				for (int i = 0; i < 4; i++) hearts.add(new Heart());
			}
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			// soft darker pink shadow
			g2.setColor(new Color(220, 160, 180, hovered ? 90 : 50));
			g2.fillRoundRect(4, 5, getWidth() - 6, getHeight() - 6, 16, 16);

			// card body
			g2.setColor(hovered ? baseColor.brighter() : baseColor);
			g2.fillRoundRect(0, 0, getWidth() - 6, getHeight() - 6, 14, 14);

			// border
			g2.setColor(hovered ? new Color(255, 140, 170) : new Color(255, 200, 215));
			g2.setStroke(new BasicStroke(hovered ? 2.5f : 1.5f));
			g2.drawRoundRect(0, 0, getWidth() - 7, getHeight() - 7, 14, 14);

			g2.dispose();
			super.paintComponent(g);

			// effects on top
			g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			phase += 0.03f;

			if (game.isNew()) {
				g2.setColor(new Color(255, 255, 150, 150));
				for (int i = 0; i < 3; i++) {
					float a = phase * 1.5f + i * 1.5f;
					int x = 12 + (int) ((Math.sin(a) * 0.5 + 0.5) * (getWidth() - 36));
					int y = 12 + (int) ((Math.cos(a * 0.5) * 0.5 + 0.5) * 14);
					g2.fillRect(x, y, 2, 2);
				}
			}
			if (game.favorite) {
				for (Heart h : hearts) {
					h.update();
					h.draw(g2);
				}
			}
			g2.dispose();
		}

		class Heart {
			float x, y, vy, alpha;
			Heart() { reset(); }
			void reset() {
				x = 0.12f + rand.nextFloat() * 0.76f;
				y = 0.18f + rand.nextFloat() * 0.45f;
				vy = -0.001f - rand.nextFloat() * 0.0018f;
				alpha = 0;
			}
			void update() {
				y += vy;
				if (alpha < 140) alpha += 2.5f;
				if (y < 0.06f) {
					alpha -= 3f;
					if (alpha <= 0) reset();
				}
			}
			void draw(Graphics2D g) {
				int px = (int) (x * (getWidth() - 30)) + 10;
				int py = (int) (y * (getHeight() - 70)) + 14;
				int a = Math.max(0, Math.min(160, (int) alpha));
				g.setColor(new Color(255, 110, 150, a));
				g.fillOval(px, py, 5, 4);
				g.fillOval(px + 3, py, 5, 4);
				g.fillPolygon(new int[]{px, px + 8, px + 4}, new int[]{py + 3, py + 3, py + 7}, 3);
			}
		}
	}

	private class Sakura {
		float x, y, speed, size, sway;
		Sakura() { reset(true); }
		void reset(boolean randomY) {
			boolean left = rand.nextBoolean();
			x = left ? rand.nextInt(38) : getWidth() - 38 + rand.nextInt(38);
			y = randomY ? rand.nextInt(Math.max(1, getHeight())) : -6;
			speed = 0.35f + rand.nextFloat() * 0.7f;
			size = 2.8f + rand.nextFloat() * 2.2f;
			sway = (rand.nextFloat() - 0.5f) * 0.25f;
		}
		void update() {
			y += speed;
			x += sway;
			if (y > getHeight() + 5) reset(false);
		}
		void draw(Graphics2D g) {
			g.setColor(new Color(255, 182, 193, 110));
			g.fillOval((int) x, (int) y, (int) size, (int) (size * 0.48));
		}
	}

	private class BackgroundPanel extends JPanel {
		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			if (backgroundImage != null) {
				g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
			} else {
				g.setColor(FALLBACK_BG);
				g.fillRect(0, 0, getWidth(), getHeight());
			}
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			for (Sakura s : petals) s.draw(g2);
			for (TrailPoint tp : trail) {
				int alpha = Math.min(150, tp.life * 6);
				if (tp.sparkle) {
					g2.setColor(new Color(255, 255, 190, alpha));
					g2.fillRect(tp.x - 1, tp.y - 1, 3, 3);
				} else {
					g2.setColor(new Color(255, 255, 255, alpha));
					int s = 2 + tp.life / 9;
					g2.fillOval(tp.x - s / 2, tp.y - s / 2, s, s);
				}
			}
			g2.dispose();
		}
	}

	private class LoadingPanel extends JPanel {
		private float angle = 0;
		private javax.swing.Timer rot;
		LoadingPanel() {
			setBackground(new Color(255, 230, 240));
			rot = new javax.swing.Timer(35, e -> { angle += 0.06f; repaint(); });
			rot.start();
		}
		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int cx = getWidth() / 2;
			int cy = getHeight() / 2 - 10;
			float scaleX = (float) Math.abs(Math.cos(angle));
			if (scaleX < 0.08f) scaleX = 0.08f;
			AffineTransform old = g2.getTransform();
			g2.translate(cx, cy);
			g2.scale(scaleX, 1.0);
			g2.setColor(new Color(255, 105, 140));
			g2.fillRect(-18, -9, 13, 13);
			g2.fillRect(5, -9, 13, 13);
			g2.fillRect(-12, 4, 25, 11);
			g2.fillRect(-5, 15, 10, 8);
			g2.setTransform(old);
			g2.setFont(new Font("Monospaced", Font.BOLD, 16));
			g2.setColor(new Color(120, 70, 90));
			String text = "Loading…";
			g2.drawString(text, cx - g2.getFontMetrics().stringWidth(text) / 2, cy + 64);
			g2.dispose();
		}
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
			catch (Exception ignored) {}
			new CuteLauncher();
		});
	}
}