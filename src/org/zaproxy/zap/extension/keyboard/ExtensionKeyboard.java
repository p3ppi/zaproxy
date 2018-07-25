/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.zap.extension.keyboard;

import java.awt.Component;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import org.apache.commons.collections.map.ReferenceMap;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.extension.ExtensionAdaptor;
import org.parosproxy.paros.extension.ExtensionHook;
import org.parosproxy.paros.view.View;
import org.zaproxy.zap.utils.DesktopUtils;
import org.zaproxy.zap.view.ZapMenuItem;

public class ExtensionKeyboard extends ExtensionAdaptor {

	private static final Logger logger = Logger.getLogger(ExtensionKeyboard.class);
	
	public static final String NAME = "ExtensionKeyboard";
	
	private OptionsKeyboardShortcutPanel optionsKeyboardPanel = null;
	private KeyboardParam keyboardParam = null;
	private ReferenceMap map = new ReferenceMap();
	private KeyboardAPI api = null;

	/**
	 * The menus with duplicated default accelerator.
	 * 
	 * @see #getDefaultAccelerator(ZapMenuItem)
	 */
	private List<JMenuItem> menusDupDefaultAccelerator;

	public ExtensionKeyboard() {
		super(NAME);
	}

	@Override
	public String getUIName() {
		return Constant.messages.getString("keyboard.name");
	}
	
	@Override
	public void hook(ExtensionHook extensionHook) {
	    if (getView() != null) {
	    	// Usually options are loaded in daemon mode, but really no point for keyboard shortcuts;)
		    extensionHook.addOptionsParamSet(getKeyboardParam());
	        extensionHook.getHookView().addOptionPanel(getOptionsKeyboardPanel());
	        
	        // Ditto the API
	        api = new KeyboardAPI(this);
	        extensionHook.addApiImplementor(api);
	    }
	}
	
	protected KeyboardParam getKeyboardParam() {
		if (keyboardParam == null) {
			keyboardParam = new KeyboardParam();
		}
		return keyboardParam;
	}

	@Override
	public void postInit() {
		if (View.isInitialised()) {
			logger.info("Initializing keyboard shortcuts");
			initAllMenuItems(View.getSingleton().getMainFrame().getMainMenuBar().getMenuFile());
			initAllMenuItems(View.getSingleton().getMainFrame().getMainMenuBar().getMenuEdit());
			initAllMenuItems(View.getSingleton().getMainFrame().getMainMenuBar().getMenuAnalyse());
			initAllMenuItems(View.getSingleton().getMainFrame().getMainMenuBar().getMenuReport());
			initAllMenuItems(View.getSingleton().getMainFrame().getMainMenuBar().getMenuTools());
			initAllMenuItems(View.getSingleton().getMainFrame().getMainMenuBar().getMenuView());
			initAllMenuItems(View.getSingleton().getMainFrame().getMainMenuBar().getMenuHelp());
		}
	}
	
	public void registerMenuItem(ZapMenuItem zme) {
		String identifier = zme.getIdentifier();
		if (identifier != null) {
			validateDefaultAccelerator(zme);
			setConfiguredAccelerator(zme);
			this.map.put(identifier, new KeyboardMapping(zme));
		} else {
			logger.warn("ZapMenuItem \"" + zme.getName() + "\" has a null identifier.");
		}
	}

	/**
	 * Validates that the given menu item does not have a duplicated default accelerator.
	 * <p>
	 * Duplicated default accelerators are ignored when configuring the menus.
	 *
	 * @param zme the menu item to validate.
	 * @see #menusDupDefaultAccelerator
	 */
	private void validateDefaultAccelerator(ZapMenuItem zme) {
		KeyStroke ks = zme.getDefaultAccelerator();
		if (ks == null) {
			return;
		}

		for (Object obj : map.values()) {
			KeyboardMapping km = (KeyboardMapping) obj;
			KeyStroke kmKs = km.getDefaultKeyStroke();
			if (kmKs != null && kmKs.getKeyCode() == ks.getKeyCode() && kmKs.getModifiers() == ks.getModifiers()) {
				String msg = String.format(
						"Menus %s and %s use the same default accelerator: %s",
						zme.getIdentifier(),
						km.getIdentifier(),
						ks);
				logger.log(Constant.isDevMode() ? Level.ERROR : Level.WARN, msg);
				if (menusDupDefaultAccelerator == null) {
					menusDupDefaultAccelerator = new ArrayList<>();
				}
				menusDupDefaultAccelerator.add(zme);
				if (zme.getDefaultAccelerator().equals(zme.getAccelerator())) {
					zme.setAccelerator(null);
				}
				return;
			}
		}
	}

	private void initAllMenuItems(JMenu menu) {
		for (Component c: menu.getMenuComponents()) {
			if (c instanceof ZapMenuItem) {
				this.registerMenuItem((ZapMenuItem)c);
				
			} else if (c instanceof JMenu) {
				initAllMenuItems((JMenu)c);
				
			} else if (c instanceof JMenuItem) {
				JMenuItem menuItem = (JMenuItem) c;
				logger.debug("Unable to set accelerators on menu " + menuItem.getText());
			}
		}
	}
	
	private void setConfiguredAccelerator(ZapMenuItem menuItem) {
		KeyStroke ks = this.getKeyboardParam().getShortcut(menuItem.getIdentifier());
		if (ks == null) {
			return;
		}

		if (ks.getKeyCode() == 0) {
			// Used to indicate no accelerator should be used
			logger.debug("Cleaning menu " + menuItem.getIdentifier() + " accelerator");
			ks = null;
		} else {
			logger.debug("Setting menu " + menuItem.getIdentifier() + " accelerator to " + ks.toString());
		}
		menuItem.setAccelerator(ks);
	}

	public List<KeyboardShortcut> getShortcuts() {
		return this.getShortcuts(false);
	}

	public List<KeyboardShortcut> getShortcuts(boolean reset) {
		if (View.isInitialised()) {
			List<KeyboardShortcut> kss = new ArrayList<KeyboardShortcut>();
			
			addAllMenuItems(kss, View.getSingleton().getMainFrame().getMainMenuBar().getMenuFile(), reset);
			addAllMenuItems(kss, View.getSingleton().getMainFrame().getMainMenuBar().getMenuEdit(), reset);
			addAllMenuItems(kss, View.getSingleton().getMainFrame().getMainMenuBar().getMenuAnalyse(), reset);
			addAllMenuItems(kss, View.getSingleton().getMainFrame().getMainMenuBar().getMenuReport(), reset);
			addAllMenuItems(kss, View.getSingleton().getMainFrame().getMainMenuBar().getMenuTools(), reset);
			addAllMenuItems(kss, View.getSingleton().getMainFrame().getMainMenuBar().getMenuView(), reset);
			addAllMenuItems(kss, View.getSingleton().getMainFrame().getMainMenuBar().getMenuHelp(), reset);
			
			return kss;
		}
		return null;
	}
	
	private void addAllMenuItems(List<KeyboardShortcut> kss, JMenu menu, boolean reset) {
		for (Component c: menu.getMenuComponents()) {
			if (c instanceof ZapMenuItem) {
				kss.add(menuToShortcut((ZapMenuItem)c, reset));
				
			} else if (c instanceof JMenu) {
				addAllMenuItems(kss, (JMenu)c, reset);
				
			} else if (c instanceof JMenuItem) {
				JMenuItem menuItem = (JMenuItem) c;
				logger.debug("Unable to set accelerators on menu " + menuItem.getText());
				
			}
		}
	}
	
	private KeyboardShortcut menuToShortcut(ZapMenuItem menuItem, boolean reset) {
		if (reset) {
			return new KeyboardShortcut(menuItem.getIdentifier(), menuItem.getText(), getDefaultAccelerator(menuItem));
		}
		
		setConfiguredAccelerator(menuItem);
		return new KeyboardShortcut(menuItem.getIdentifier(), menuItem.getText(), menuItem.getAccelerator());
		
	}

	/**
	 * Gets the default accelerator of the given menu, taken into account duplicated default accelerators.
	 *
	 * @param menuItem the menu item to return the default accelerator
	 * @return the KeyStroke or {@code null} if duplicated or does not have a default.
	 */
	private KeyStroke getDefaultAccelerator(ZapMenuItem menuItem) {
		if (menusDupDefaultAccelerator != null && menusDupDefaultAccelerator.contains(menuItem)) {
			return null;
		}
		return menuItem.getDefaultAccelerator();
	}
	
	public KeyStroke getShortcut(String identifier) {
		KeyboardMapping mapping = (KeyboardMapping) this.map.get(identifier);
		if (mapping == null) {
			return null;
		}
		return mapping.getKeyStroke();
	}
	
	public void setShortcut(String identifier, KeyStroke ks) {
		KeyboardMapping mapping = (KeyboardMapping) this.map.get(identifier);
		if (mapping == null) {
			logger.error("No mapping found for keyboard shortcut: " + identifier);
			return;
		}
		mapping.setKeyStroke(ks);
		this.getKeyboardParam().setShortcut(identifier, ks);
	}
	
	private OptionsKeyboardShortcutPanel getOptionsKeyboardPanel() {
		if (optionsKeyboardPanel == null) {
			optionsKeyboardPanel = new OptionsKeyboardShortcutPanel(this);
		}
		return optionsKeyboardPanel;
	}
	
	public void displayCheatsheetSortedByAction() {
		try {
			DesktopUtils.openUrlInBrowser(api.getCheatSheetActionURI());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}
	
	public void displayCheatsheetSortedByKey() {
		try {
			DesktopUtils.openUrlInBrowser(api.getCheatSheetKeyURI());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}
	
	@Override
	public String getAuthor() {
		return Constant.ZAP_TEAM;
	}

	@Override
	public String getDescription() {
		return Constant.messages.getString("keyboard.desc");
	}

	@Override
	public URL getURL() {
		try {
			return new URL(Constant.ZAP_HOMEPAGE);
		} catch (MalformedURLException e) {
			return null;
		}
	}

	/**
	 * No database tables used, so all supported
	 */
	@Override
	public boolean supportsDb(String type) {
		return true;
	}
}
