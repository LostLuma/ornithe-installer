/*
 * Copyright 2021 QuiltMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.installer.gui.swing;

import org.quiltmc.installer.LoaderType;
import org.quiltmc.installer.Localization;
import org.quiltmc.installer.OrnitheMeta;
import org.quiltmc.installer.VersionManifest;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The logic side of the swing gui for the installer.
 */
public final class SwingInstaller extends JFrame {
	private final ClientPanel clientPanel;
	private final ServerPanel serverPanel;

	public static void run() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | UnsupportedLookAndFeelException | IllegalAccessException |
                 InstantiationException e) {
			e.printStackTrace();
		}

		new SwingInstaller();
	}

	private SwingInstaller() {
		try {
			// Use a tabbed pane for client/server menus
			JTabbedPane contentPane = new JTabbedPane(JTabbedPane.TOP);
			contentPane.addTab(Localization.get("tab.client"), null, this.clientPanel = new ClientPanel(this), Localization.get("tab.client.tooltip"));
			contentPane.addTab(Localization.get("tab.server"), null, this.serverPanel = new ServerPanel(this), Localization.get("tab.server.tooltip"));

			// Start version lookup before we show the window
			// Lookup loader and intermediary
			Set<OrnitheMeta.Endpoint<?>> endpoints = new HashSet<>();
			for (LoaderType type : LoaderType.values()) {
				endpoints.add(OrnitheMeta.loaderVersionsEndpoint(type));
			}
			endpoints.add(OrnitheMeta.INTERMEDIARY_VERSIONS_ENDPOINT);

			OrnitheMeta.create(OrnitheMeta.ORNITHE_META_URL, endpoints).thenAcceptBothAsync(VersionManifest.create(), ((quiltMeta, manifest) -> {
				Map<LoaderType, List<String>> loaderVersions = new EnumMap<>(LoaderType.class);
				for (LoaderType type : LoaderType.values()) {
					loaderVersions.put(type, quiltMeta.getEndpoint(OrnitheMeta.loaderVersionsEndpoint(type)).stream().filter(v -> {
						if (type != LoaderType.QUILT) {
							return true;
						}
						// TODO HACK HACK HACK
						// This is a hack to filter out old versions of Loader which we know will not support finding the main class.
						return !(v.startsWith("0.16.0-beta.") && v.length() == 13 && v.charAt(12) != '9');
					}).collect(Collectors.toList()));
				}
				Map<String, String> intermediaryVersions = quiltMeta.getEndpoint(OrnitheMeta.INTERMEDIARY_VERSIONS_ENDPOINT);

				this.clientPanel.receiveVersions(manifest, loaderVersions, intermediaryVersions);
				this.serverPanel.receiveVersions(manifest, loaderVersions, intermediaryVersions);
			})).exceptionally(e -> {
				e.printStackTrace();
				AbstractPanel.displayError(this, e);
				return null;
			});

			this.setContentPane(contentPane);
			this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
			this.setTitle(Localization.get("title"));
			this.setIconImage(Toolkit.getDefaultToolkit().getImage(ClassLoader.getSystemClassLoader().getResource("icon.png")));
			this.pack();
			this.setLocationRelativeTo(null); // Center on screen
			this.setResizable(false);
			this.setVisible(true);
		} catch (HeadlessException e) {
			System.exit(1); // Don't know how we got here
			throw new IllegalStateException(); // Make javac happy
		} catch (Throwable t) {
			AbstractPanel.displayError(this, t);
			System.exit(1); // TODO: May be overkill?
			throw new IllegalStateException(); // Make javac happy
		}
	}
}
