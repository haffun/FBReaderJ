/*
 * Copyright (C) 2009-2013 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.android.fbreader;

import java.lang.reflect.*;
import java.util.*;

import android.app.*;
import android.util.Log;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.*;
import android.widget.RelativeLayout;

import org.geometerplus.zlibrary.core.filesystem.ZLFile;
import org.geometerplus.zlibrary.core.filetypes.FileType;
import org.geometerplus.zlibrary.core.filetypes.FileTypeCollection;
import org.geometerplus.zlibrary.core.image.ZLImage;
import org.geometerplus.zlibrary.core.library.ZLibrary;
import org.geometerplus.zlibrary.core.resources.ZLResource;
import org.geometerplus.zlibrary.core.util.MimeType;

import org.geometerplus.zlibrary.text.view.ZLTextView;
import org.geometerplus.zlibrary.text.hyphenation.ZLTextHyphenator;

import org.geometerplus.zlibrary.ui.android.R;
import org.geometerplus.zlibrary.ui.android.application.ZLAndroidApplicationWindow;
import org.geometerplus.zlibrary.ui.android.library.*;
import org.geometerplus.zlibrary.ui.android.view.AndroidFontUtil;

import org.geometerplus.fbreader.book.*;
import org.geometerplus.fbreader.bookmodel.BookModel;
import org.geometerplus.fbreader.fbreader.*;
import org.geometerplus.fbreader.formats.*;
import org.geometerplus.fbreader.library.Library;
import org.geometerplus.fbreader.tips.TipsManager;

import org.geometerplus.android.fbreader.api.*;
import org.geometerplus.android.fbreader.library.KillerCallback;
import org.geometerplus.android.fbreader.libraryService.BookCollectionShadow;
import org.geometerplus.android.fbreader.libraryService.SQLiteBooksDatabase;
import org.geometerplus.android.fbreader.tips.TipsActivity;

import org.geometerplus.android.util.UIUtil;

public final class FBReader extends Activity {
	private class ExtFileOpener implements FBReaderApp.ExternalFileOpener {
		private void showErrorDialog(final String errName) {
			runOnUiThread(new Runnable() {
				public void run() {
					final String title = ZLResource.resource("errorMessage").getResource(errName).getValue();
					final AlertDialog dialog = new AlertDialog.Builder(FBReader.this)
						.setTitle(title)
						.setIcon(0)
						.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
							}
						})
						.create();
					if (myIsPaused) {
						myDialogToShow = dialog;
					} else {
						dialog.show();
					}
				}
			});
		}

		public boolean openFile(ZLFile f, String appData) {
			if (f == null) {
				showErrorDialog("unzipFailed");
				return false;
			}
			String extension = f.getExtension();
			Uri uri = Uri.parse("file://" + f.getPath());
			Intent LaunchIntent = new Intent(Intent.ACTION_VIEW);
			LaunchIntent.setPackage(appData);
			LaunchIntent.setData(uri);
			FileType ft = FileTypeCollection.Instance.typeForFile(f);
			for (MimeType type : ft.mimeTypes()) {
				LaunchIntent.setDataAndType(uri, type.Name);
				try {
					startActivity(LaunchIntent);
					return true;
				} catch (ActivityNotFoundException e) {
				}
			}
			showErrorDialog("externalNotFound");
			return false;
		}
	}

	private class PluginFileOpener implements FBReaderApp.PluginFileOpener {
		private void showErrorDialog(final String errName) {
			final String title = ZLResource.resource("errorMessage").getResource(errName).getValue();
			final AlertDialog dialog = new AlertDialog.Builder(FBReader.this)
				.setTitle(title)
				.setIcon(0)
				.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
					}
				}).create();
			if (myIsPaused) {
				myDialogToShow = dialog;
			} else {
				dialog.show();
			}
		}
		
		private void showErrorDialog(final String errName, final String appData, final long bookId) {
			runOnUiThread(new Runnable() {
				public void run() {
					final String title = ZLResource.resource("errorMessage").getResource(errName).getValue();
					final AlertDialog dialog = new AlertDialog.Builder(FBReader.this)
						.setTitle(title)
						.setIcon(0)
						.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								   Intent i = new Intent(Intent.ACTION_VIEW);
								   i.setData(Uri.parse("market://search?q=" + appData));
								   startActivity(i);
							}
						})
						.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								onPluginAbsent(bookId);
							}
						})
						.setOnCancelListener(new DialogInterface.OnCancelListener() {
							@Override
							public void onCancel(DialogInterface dialog) {
								onPluginAbsent(bookId);
							}
						})
						.create();
						if (myIsPaused) {
							myDialogToShow = dialog;
						} else {
								dialog.show();
						}
					}
			});
		}

		public void openFile(ZLFile f, String appData, String bookmark, long bookId) {
			if (f == null) {
				showErrorDialog("unzipFailed");
				return;
			}
			Uri uri = Uri.parse("file://" + f.getPath());
			Intent LaunchIntent = new Intent("android.fbreader.action.VIEW_PLUGIN");
			LaunchIntent.setPackage(appData);
			LaunchIntent.setData(uri);
			LaunchIntent.putExtra("BOOKMARK", bookmark);
			LaunchIntent.putExtra("BOOKID", bookId);
			LaunchIntent.putExtra("TITLE", Book.getById(bookId).getTitle() != null ? Book.getById(bookId).getTitle() : "");	
			FileType ft = FileTypeCollection.Instance.typeForFile(f);
			for (MimeType type : ft.mimeTypes()) {
				LaunchIntent.setDataAndType(uri, type.Name);
				try {
					startActivity(LaunchIntent);
					return;
				} catch (ActivityNotFoundException e) {
				}
			}
			showErrorDialog("noPlugin", appData, bookId);
			return;
		}

	}

	public static final String ACTION_OPEN_BOOK = "android.fbreader.action.VIEW";
	public static final String BOOK_KEY = "fbreader.book";
	public static final String BOOKMARK_KEY = "fbreader.bookmark";
	
	static final int ACTION_BAR_COLOR = Color.DKGRAY;

	public static final int REQUEST_PREFERENCES = 1;
	public static final int REQUEST_BOOK_INFO = 2;
	public static final int REQUEST_CANCEL_MENU = 3;

	public static final int RESULT_DO_NOTHING = RESULT_FIRST_USER;
	public static final int RESULT_REPAINT = RESULT_FIRST_USER + 1;
	public static final int RESULT_RELOAD_BOOK = RESULT_FIRST_USER + 2;

	public static void openBookActivity(Context context, Book book, Bookmark bookmark) {
		context.startActivity(
			new Intent(context, FBReader.class)
				.setAction(ACTION_OPEN_BOOK)
				.putExtra(BOOK_KEY, SerializerUtil.serialize(book))
				.putExtra(BOOKMARK_KEY, SerializerUtil.serialize(bookmark))
				.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
		);
	}

	private static ZLAndroidLibrary getZLibrary() {
		return (ZLAndroidLibrary)ZLAndroidLibrary.Instance();
	}

	private FBReaderApp myFBReaderApp;
	private volatile Book myBook;

	private int myFullScreenFlag;

	private boolean myIsPaused = false;
	private AlertDialog myDialogToShow = null;

	private boolean myNeedToOpenFile = false;
	private Intent myIntentToOpen = null;

	private static final String PLUGIN_ACTION_PREFIX = "___";
	private final List<PluginApi.ActionInfo> myPluginActions =
		new LinkedList<PluginApi.ActionInfo>();
	private final BroadcastReceiver myPluginInfoReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final ArrayList<PluginApi.ActionInfo> actions = getResultExtras(true).<PluginApi.ActionInfo>getParcelableArrayList(PluginApi.PluginInfo.KEY);
			if (actions != null) {
				synchronized (myPluginActions) {
					int index = 0;
					while (index < myPluginActions.size()) {
						myFBReaderApp.removeAction(PLUGIN_ACTION_PREFIX + index++);
					}
					myPluginActions.addAll(actions);
					index = 0;
					for (PluginApi.ActionInfo info : myPluginActions) {
						myFBReaderApp.addAction(
							PLUGIN_ACTION_PREFIX + index++,
							new RunPluginAction(FBReader.this, myFBReaderApp, info.getId())
						);
					}
				}
			}
		}
	};

	private synchronized void openBook(Intent intent, Runnable action, boolean force) {
		if (!force && myBook != null) {
			return;
		}

		myBook = SerializerUtil.deserializeBook(intent.getStringExtra(BOOK_KEY));
		if (intent.getStringExtra(BOOKMARK_KEY) != null) {
			Log.d("bookmark", intent.getStringExtra(BOOKMARK_KEY));
		} else {
			Log.d("bookmark", "null");
		}
		final Bookmark bookmark =
			SerializerUtil.deserializeBookmark(intent.getStringExtra(BOOKMARK_KEY));
		if (bookmark == null) {
			Log.d("bookmark", "null!!!!1111");
		}
		if (myBook == null) {
			final Uri data = intent.getData();
			if (data != null) {
				myBook = createBookForFile(ZLFile.createFileByPath(data.getPath()));
			}
		}
		Log.d("fbreader", "filePath");
		if (myBook != null) {
			Log.d("fbreader", myBook.File.getPath());
		}
		myFBReaderApp.openBook(myBook, bookmark, action);
	}

	private Book createBookForFile(ZLFile file) {
		if (file == null) {
			return null;
		}
		Book book = Book.getByFile(file);
		if (book != null) {
			return book;
		}
//		if (file.isArchive()) {
//			for (ZLFile child : file.children()) {
//				book = Book.getByFile(child);
//				if (book != null) {
//					return book;
//				}
//			}
//		}
		return null;
	}

	private Runnable getPostponedInitAction() {
		return new Runnable() {
			public void run() {
				runOnUiThread(new Runnable() {
					public void run() {
						new TipRunner().start();
						DictionaryUtil.init(FBReader.this);
					}
				});
			}
		};
	}

	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler(this));

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);
		setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

		getZLibrary().setActivity(this);

		myFBReaderApp = (FBReaderApp)FBReaderApp.Instance();
		if (myFBReaderApp == null) {
			if (SQLiteBooksDatabase.Instance() == null) {
				new SQLiteBooksDatabase(this, "READER");
			}
			myFBReaderApp = new FBReaderApp(new BookCollectionShadow());
		}
		myBook = null;

		final ZLAndroidApplication androidApplication = (ZLAndroidApplication)getApplication();
		if (androidApplication.myMainWindow == null) {
			androidApplication.myMainWindow = new ZLAndroidApplicationWindow(myFBReaderApp);
			myFBReaderApp.initWindow();
		}

		new Thread() {
			public void run() {
				getPostponedInitAction().run();
			}
		}.start();

		myFBReaderApp.getViewWidget().repaint();
//		if (!myFBReaderApp.externalFileOpenerIsSet()) {
			myFBReaderApp.setExternalFileOpener(new ExtFileOpener());
//		}
//		if (!myFBReaderApp.pluginFileOpenerIsSet()) {
			myFBReaderApp.setPluginFileOpener(new PluginFileOpener());
//		}

		myNeedToOpenFile = true;
		myIntentToOpen = getIntent();
		myNeedToSkipPlugin = true;

		myFullScreenFlag =
			getZLibrary().ShowStatusBarOption.getValue() ? 0 : WindowManager.LayoutParams.FLAG_FULLSCREEN;
		getWindow().setFlags(
			WindowManager.LayoutParams.FLAG_FULLSCREEN, myFullScreenFlag
		);

		if (myFBReaderApp.getPopupById(TextSearchPopup.ID) == null) {
			new TextSearchPopup(myFBReaderApp);
		}
		if (myFBReaderApp.getPopupById(SelectionPopup.ID) == null) {
			new SelectionPopup(myFBReaderApp);
		}

		myFBReaderApp.addAction(ActionCode.SHOW_LIBRARY, new ShowLibraryAction(this, myFBReaderApp));
		myFBReaderApp.addAction(ActionCode.SHOW_PREFERENCES, new ShowPreferencesAction(this, myFBReaderApp));
		myFBReaderApp.addAction(ActionCode.SHOW_BOOK_INFO, new ShowBookInfoAction(this, myFBReaderApp));
		myFBReaderApp.addAction(ActionCode.SHOW_TOC, new ShowTOCAction(this, myFBReaderApp));
		myFBReaderApp.addAction(ActionCode.SHOW_BOOKMARKS, new ShowBookmarksAction(this, myFBReaderApp));
		myFBReaderApp.addAction(ActionCode.SHOW_NETWORK_LIBRARY, new ShowNetworkLibraryAction(this, myFBReaderApp));

		myFBReaderApp.addAction(ActionCode.SHOW_MENU, new ShowMenuAction(this, myFBReaderApp));
		myFBReaderApp.addAction(ActionCode.SHOW_NAVIGATION, new ShowNavigationAction(this, myFBReaderApp));
		myFBReaderApp.addAction(ActionCode.SEARCH, new SearchAction(this, myFBReaderApp));
		myFBReaderApp.addAction(ActionCode.SHARE_BOOK, new ShareBookAction(this, myFBReaderApp));

		myFBReaderApp.addAction(ActionCode.SELECTION_SHOW_PANEL, new SelectionShowPanelAction(this, myFBReaderApp));
		myFBReaderApp.addAction(ActionCode.SELECTION_HIDE_PANEL, new SelectionHidePanelAction(this, myFBReaderApp));
		myFBReaderApp.addAction(ActionCode.SELECTION_COPY_TO_CLIPBOARD, new SelectionCopyAction(this, myFBReaderApp));
		myFBReaderApp.addAction(ActionCode.SELECTION_SHARE, new SelectionShareAction(this, myFBReaderApp));
		myFBReaderApp.addAction(ActionCode.SELECTION_TRANSLATE, new SelectionTranslateAction(this, myFBReaderApp));
		myFBReaderApp.addAction(ActionCode.SELECTION_BOOKMARK, new SelectionBookmarkAction(this, myFBReaderApp));

		myFBReaderApp.addAction(ActionCode.PROCESS_HYPERLINK, new ProcessHyperlinkAction(this, myFBReaderApp));

		myFBReaderApp.addAction(ActionCode.SHOW_CANCEL_MENU, new ShowCancelMenuAction(this, myFBReaderApp));

		myFBReaderApp.addAction(ActionCode.SET_SCREEN_ORIENTATION_SYSTEM, new SetScreenOrientationAction(this, myFBReaderApp, ZLibrary.SCREEN_ORIENTATION_SYSTEM));
		myFBReaderApp.addAction(ActionCode.SET_SCREEN_ORIENTATION_SENSOR, new SetScreenOrientationAction(this, myFBReaderApp, ZLibrary.SCREEN_ORIENTATION_SENSOR));
		myFBReaderApp.addAction(ActionCode.SET_SCREEN_ORIENTATION_PORTRAIT, new SetScreenOrientationAction(this, myFBReaderApp, ZLibrary.SCREEN_ORIENTATION_PORTRAIT));
		myFBReaderApp.addAction(ActionCode.SET_SCREEN_ORIENTATION_LANDSCAPE, new SetScreenOrientationAction(this, myFBReaderApp, ZLibrary.SCREEN_ORIENTATION_LANDSCAPE));
		if (ZLibrary.Instance().supportsAllOrientations()) {
			myFBReaderApp.addAction(ActionCode.SET_SCREEN_ORIENTATION_REVERSE_PORTRAIT, new SetScreenOrientationAction(this, myFBReaderApp, ZLibrary.SCREEN_ORIENTATION_REVERSE_PORTRAIT));
			myFBReaderApp.addAction(ActionCode.SET_SCREEN_ORIENTATION_REVERSE_LANDSCAPE, new SetScreenOrientationAction(this, myFBReaderApp, ZLibrary.SCREEN_ORIENTATION_REVERSE_LANDSCAPE));
		}
		if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
		} else {
			if ("android.fbreader.action.CLOSE".equals(getIntent().getAction()) ) {
				myCancelCalled = true;
			} else if ("android.fbreader.action.PLUGIN_CRASH".equals(getIntent().getAction())) {
				Log.d("fbj", "crash in oncreate");
				long bookid = getIntent().getLongExtra("BOOKID", -1);
				Library.Instance().removeBookFromRecentList(Book.getById(bookid));
				myNeedToSkipPlugin = true;
				myFBReaderApp.Model = null;
				myFBReaderApp.openBook(Library.Instance().getRecentBook(), null, null);
			}
		}
	}

 	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		final ZLAndroidLibrary zlibrary = (ZLAndroidLibrary)ZLibrary.Instance();
		if (!zlibrary.isKindleFire() && !zlibrary.ShowStatusBarOption.getValue()) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		}
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void onOptionsMenuClosed(Menu menu) {
		super.onOptionsMenuClosed(menu);
		final ZLAndroidLibrary zlibrary = (ZLAndroidLibrary)ZLibrary.Instance();
		if (!zlibrary.isKindleFire() && !zlibrary.ShowStatusBarOption.getValue()) {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final ZLAndroidLibrary zlibrary = (ZLAndroidLibrary)ZLibrary.Instance();
		if (!zlibrary.isKindleFire() && !zlibrary.ShowStatusBarOption.getValue()) {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		}
		return super.onOptionsItemSelected(item);
	}

	private boolean myCancelCalled = false;
	private boolean myNeedToSkipPlugin = false;

	private int myCancelAction = -1;

	@Override
	protected void onNewIntent(final Intent intent) {
		final String action = intent.getAction();
		final Uri data = intent.getData();

		if (Intent.ACTION_VIEW.equals(action)) {
			myNeedToSkipPlugin = true;
		}

		if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
			super.onNewIntent(intent);
		} else if (Intent.ACTION_VIEW.equals(action) || ACTION_OPEN_BOOK.equals(action)) {
			myNeedToOpenFile = true;
			myIntentToOpen = intent;
			myNeedToSkipPlugin = true;
		} else if (Intent.ACTION_VIEW.equals(action)
					&& data != null && "fbreader-action".equals(data.getScheme())) {
			myFBReaderApp.runAction(data.getEncodedSchemeSpecificPart(), data.getFragment());
		} else if (Intent.ACTION_SEARCH.equals(action)) {
			final String pattern = intent.getStringExtra(SearchManager.QUERY);
			final Runnable runnable = new Runnable() {
				public void run() {
					final TextSearchPopup popup = (TextSearchPopup)myFBReaderApp.getPopupById(TextSearchPopup.ID);
					popup.initPosition();
					myFBReaderApp.TextSearchPatternOption.setValue(pattern);
					if (myFBReaderApp.getTextView().search(pattern, true, false, false, false) != 0) {
						runOnUiThread(new Runnable() {
							public void run() {
								hideBars();
								myFBReaderApp.showPopup(popup.getId());
							}
						});
					} else {
						runOnUiThread(new Runnable() {
							public void run() {
								UIUtil.showErrorMessage(FBReader.this, "textNotFound");
								popup.StartPosition = null;
							}
						});
					}
				}
			};
			UIUtil.wait("search", runnable, this);
		} else if ("android.fbreader.action.CLOSE".equals(intent.getAction())) {
			myCancelCalled = true;
			myCancelAction = intent.getIntExtra("value", -1);
		} else if ("android.fbreader.action.PLUGIN_CRASH".equals(intent.getAction())) {
			Log.d("fbj", "crash");
			long bookid = intent.getLongExtra("BOOKID", -1);
			Library.Instance().removeBookFromRecentList(Book.getById(bookid));
			myNeedToSkipPlugin = true;
			myFBReaderApp.Model = null;
			myFBReaderApp.openBook(Library.Instance().getRecentBook(), null, null);
		} else {
			super.onNewIntent(intent);
			if (Intent.ACTION_VIEW.equals(action) || "android.fbreader.action.VIEW".equals(action)) {
				myNeedToOpenFile = true;
				myIntentToOpen = intent;
				myNeedToSkipPlugin = true;
				if (intent.getBooleanExtra("KILL_PLUGIN", false)) {
					Log.d("fbreader", "killing plugin");
					if (myFBReaderApp.Model != null && myFBReaderApp.Model.Book != null) {
						final FormatPlugin p = PluginCollection.Instance().getPlugin(myFBReaderApp.Model.Book.File);
						if (p.type() == FormatPlugin.Type.PLUGIN) {
							String pack = ((PluginFormatPlugin)p).getPackage();
							final Intent i = new Intent("android.fbreader.action.KILL_PLUGIN");
							i.setPackage(pack);
							Log.d("fbreader", pack);
							try {
								startActivity(i);
							} catch (ActivityNotFoundException e) {
								e.printStackTrace();
							}
						}
					}
				}
			}
		}
	}

	@Override
	protected void onStart() {
		super.onStart();

		initPluginActions();

		final ZLAndroidLibrary zlibrary = (ZLAndroidLibrary)ZLibrary.Instance();

		final int fullScreenFlag =
			zlibrary.ShowStatusBarOption.getValue() ? 0 : WindowManager.LayoutParams.FLAG_FULLSCREEN;
		if (fullScreenFlag != myFullScreenFlag) {
			finish();
			startActivity(new Intent(this, getClass()));
		}

		SetScreenOrientationAction.setOrientation(this, zlibrary.getOrientationOption().getValue());

		final RelativeLayout root = (RelativeLayout)findViewById(R.id.root_view);
		((PopupPanel)myFBReaderApp.getPopupById(TextSearchPopup.ID)).setPanelInfo(this, root);
		((PopupPanel)myFBReaderApp.getPopupById(SelectionPopup.ID)).setPanelInfo(this, root);
	}

	private void initPluginActions() {
		synchronized (myPluginActions) {
			int index = 0;
			while (index < myPluginActions.size()) {
				myFBReaderApp.removeAction(PLUGIN_ACTION_PREFIX + index++);
			}
			myPluginActions.clear();
		}

		sendOrderedBroadcast(
			new Intent(PluginApi.ACTION_REGISTER),
			null,
			myPluginInfoReceiver,
			null,
			RESULT_OK,
			null,
			null
		);
	}

	private class TipRunner extends Thread {
		TipRunner() {
			setPriority(MIN_PRIORITY);
		}

		public void run() {
			final TipsManager manager = TipsManager.Instance();
			switch (manager.requiredAction()) {
				case Initialize:
					startActivity(new Intent(
						TipsActivity.INITIALIZE_ACTION, null, FBReader.this, TipsActivity.class
					));
					break;
				case Show:
					startActivity(new Intent(
						TipsActivity.SHOW_TIP_ACTION, null, FBReader.this, TipsActivity.class
					));
					break;
				case Download:
					manager.startDownloading();
					break;
				case None:
					break;
			}
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();

		switchWakeLock(
			getZLibrary().BatteryLevelToTurnScreenOffOption.getValue() < myFBReaderApp.getBatteryLevel()
		);
		myStartTimer = true;
		final int brightnessLevel =
			getZLibrary().ScreenBrightnessLevelOption().getValue();
		if (brightnessLevel != 0) {
			setScreenBrightness(brightnessLevel);
		} else {
			setScreenBrightnessAuto();
		}
		if (getZLibrary().DisableButtonLightsOption.getValue()) {
			setButtonLight(false);
		}

		registerReceiver(myBatteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		myIsPaused = false;
		if (myDialogToShow != null) {
			myDialogToShow.show();
			myDialogToShow = null;
		}

		SetScreenOrientationAction.setOrientation(this, ZLibrary.Instance().getOrientationOption().getValue());
		Log.d("fbreader", "onresume");
		if (myCancelCalled) {
			myCancelCalled = false;
			if (myCancelAction != -1) {
				myFBReaderApp.runCancelAction(myCancelAction - 1);
			} else {
				finish();
			}
			return;
		} else {
			if (myFBReaderApp.Model != null && myFBReaderApp.Model.Book != null) {
				final FormatPlugin p = PluginCollection.Instance().getPlugin(myFBReaderApp.Model.Book.File);
				Log.d("fbj", "onresume: current book is: " + myFBReaderApp.Model.Book.File.getPath());
				if (p.type() == FormatPlugin.Type.PLUGIN) {
					if (!myNeedToSkipPlugin) {
						Log.d("fbj", "opening book from onresume");
						myFBReaderApp.openBook(myFBReaderApp.Model.Book, null, null);
					} else {
						Log.d("fbj", "skipping");
					}
				}
			}
			myNeedToSkipPlugin = false;
		}

		try {
			sendBroadcast(new Intent(getApplicationContext(), KillerCallback.class));
		} catch (Throwable t) {
		}
		if (myNeedToOpenFile) {
			Log.d("fbj", "needtoopen");
			openBook(myIntentToOpen, null, true);
			myNeedToOpenFile = false;
			myIntentToOpen = null;
		}
		PopupPanel.restoreVisibilities(myFBReaderApp);
		ApiServerImplementation.sendEvent(this, ApiListener.EVENT_READ_MODE_OPENED);
	}

	@Override
	protected void onPause() {
		myIsPaused = true;
		unregisterReceiver(myBatteryInfoReceiver);
		myFBReaderApp.stopTimer();
		switchWakeLock(false);
		if (getZLibrary().DisableButtonLightsOption.getValue()) {
			setButtonLight(true);
		}
		myFBReaderApp.onWindowClosing();
		super.onPause();
	}

	@Override
	protected void onStop() {
		ApiServerImplementation.sendEvent(this, ApiListener.EVENT_READ_MODE_CLOSED);
		PopupPanel.removeAllWindows(myFBReaderApp, this);
		super.onStop();
	}

	@Override
	public void onLowMemory() {
		myFBReaderApp.onWindowClosing();
		super.onLowMemory();
	}

	@Override
	public boolean onSearchRequested() {
		final FBReaderApp.PopupPanel popup = myFBReaderApp.getActivePopup();
		myFBReaderApp.hideActivePopup();
		hideBars();
		final SearchManager manager = (SearchManager)getSystemService(SEARCH_SERVICE);
		manager.setOnCancelListener(new SearchManager.OnCancelListener() {
			public void onCancel() {
				if (popup != null) {
					myFBReaderApp.showPopup(popup.getId());
				}
				manager.setOnCancelListener(null);
			}
		});
		startSearch(myFBReaderApp.TextSearchPatternOption.getValue(), true, null, false);
		return true;
	}

	public void showSelectionPanel() {
		final ZLTextView view = myFBReaderApp.getTextView();
		((SelectionPopup)myFBReaderApp.getPopupById(SelectionPopup.ID))
			.move(view.getSelectionStartY(), view.getSelectionEndY());
		hideBars();
		myFBReaderApp.showPopup(SelectionPopup.ID);
	}

	public void hideSelectionPanel() {
		final FBReaderApp.PopupPanel popup = myFBReaderApp.getActivePopup();
		if (popup != null && popup.getId() == SelectionPopup.ID) {
			myFBReaderApp.hideActivePopup();
		}
	}

	private void onPreferencesUpdate(int resultCode) {
		switch (resultCode) {
			case RESULT_DO_NOTHING:
				break;
			case RESULT_REPAINT:
			{
				AndroidFontUtil.clearFontCache();
				final BookModel model = myFBReaderApp.Model;
				if (model != null) {
					final Book book = model.Book;
					if (book != null) {
						book.reloadInfoFromDatabase();
						ZLTextHyphenator.Instance().load(book.getLanguage());
					}
				}
				myFBReaderApp.clearTextCaches();
				myFBReaderApp.getViewWidget().repaint();
				break;
			}
			case RESULT_RELOAD_BOOK:
				myFBReaderApp.reloadBook();
				break;
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case REQUEST_PREFERENCES:
			case REQUEST_BOOK_INFO:
				onPreferencesUpdate(resultCode);
				break;
			case REQUEST_CANCEL_MENU:
				if (resultCode != RESULT_CANCELED && resultCode != -1) {
					myNeedToSkipPlugin = true;
				} else {
				}
				myFBReaderApp.runCancelAction(resultCode - 1);
				break;
		}
	}

	public void refresh() {
		if (myNavigationPopup != null) {
			myNavigationPopup.update();
		}
	}
	
	private NavigationPopup myNavigationPopup;
	
	boolean barsAreShown() {
		return myNavigationPopup != null;
	}
	
	void hideBars() {
		if (myNavigationPopup != null) {
			myNavigationPopup.stopNavigation();
			myNavigationPopup = null;
		}
	}

	void showBars() {
		final RelativeLayout root = (RelativeLayout)findViewById(R.id.root_view);

		if (myNavigationPopup == null) {
			myFBReaderApp.hideActivePopup();
			myNavigationPopup = new NavigationPopup(myFBReaderApp);
			myNavigationPopup.runNavigation(this, root);
		}
	}

	private Menu addSubMenu(Menu menu, String id) {
		final ZLAndroidApplication application = (ZLAndroidApplication)getApplication();
		return application.myMainWindow.addSubMenu(menu, id);
	}

	private void addMenuItem(Menu menu, String actionId, String name) {
		final ZLAndroidApplication application = (ZLAndroidApplication)getApplication();
		application.myMainWindow.addMenuItem(menu, actionId, null, name);
	}

	private void addMenuItem(Menu menu, String actionId, int iconId) {
		final ZLAndroidApplication application = (ZLAndroidApplication)getApplication();
		application.myMainWindow.addMenuItem(menu, actionId, iconId, null);
	}

	private void addMenuItem(Menu menu, String actionId) {
		final ZLAndroidApplication application = (ZLAndroidApplication)getApplication();
		application.myMainWindow.addMenuItem(menu, actionId, null, null);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		addMenuItem(menu, ActionCode.SHOW_LIBRARY, R.drawable.ic_menu_library);
		addMenuItem(menu, ActionCode.SHOW_NETWORK_LIBRARY, R.drawable.ic_menu_networklibrary);
		addMenuItem(menu, ActionCode.SHOW_TOC, R.drawable.ic_menu_toc);
		addMenuItem(menu, ActionCode.SHOW_BOOKMARKS, R.drawable.ic_menu_bookmarks);
		addMenuItem(menu, ActionCode.SWITCH_TO_NIGHT_PROFILE, R.drawable.ic_menu_night);
		addMenuItem(menu, ActionCode.SWITCH_TO_DAY_PROFILE, R.drawable.ic_menu_day);
		addMenuItem(menu, ActionCode.SEARCH, R.drawable.ic_menu_search);
		addMenuItem(menu, ActionCode.SHARE_BOOK, R.drawable.ic_menu_search);
		addMenuItem(menu, ActionCode.SHOW_PREFERENCES);
		addMenuItem(menu, ActionCode.SHOW_BOOK_INFO);
		final Menu subMenu = addSubMenu(menu, "screenOrientation");
		addMenuItem(subMenu, ActionCode.SET_SCREEN_ORIENTATION_SYSTEM);
		addMenuItem(subMenu, ActionCode.SET_SCREEN_ORIENTATION_SENSOR);
		addMenuItem(subMenu, ActionCode.SET_SCREEN_ORIENTATION_PORTRAIT);
		addMenuItem(subMenu, ActionCode.SET_SCREEN_ORIENTATION_LANDSCAPE);
		if (ZLibrary.Instance().supportsAllOrientations()) {
			addMenuItem(subMenu, ActionCode.SET_SCREEN_ORIENTATION_REVERSE_PORTRAIT);
			addMenuItem(subMenu, ActionCode.SET_SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
		}
		addMenuItem(menu, ActionCode.INCREASE_FONT);
		addMenuItem(menu, ActionCode.DECREASE_FONT);
//		addMenuItem(menu, ActionCode.SHOW_NAVIGATION);
		synchronized (myPluginActions) {
			int index = 0;
			for (PluginApi.ActionInfo info : myPluginActions) {
				if (info instanceof PluginApi.MenuActionInfo) {
					addMenuItem(
						menu,
						PLUGIN_ACTION_PREFIX + index++,
						((PluginApi.MenuActionInfo)info).MenuItemName
					);
				}
			}
		}

		final ZLAndroidApplication application = (ZLAndroidApplication)getApplication();
		application.myMainWindow.refresh();

		return true;
	}

	protected void onPluginAbsent(long bookId) {
		Library.Instance().removeBookFromRecentList(Book.getById(bookId));
		myFBReaderApp.Model = null;
		myFBReaderApp.openBook(Library.Instance().getRecentBook(), null, null);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		final View view = findViewById(R.id.main_view);
		return (view != null && view.onKeyDown(keyCode, event)) || super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		final View view = findViewById(R.id.main_view);
		return (view != null && view.onKeyUp(keyCode, event)) || super.onKeyUp(keyCode, event);
	}

	private void setButtonLight(boolean enabled) {
		try {
			final WindowManager.LayoutParams attrs = getWindow().getAttributes();
			final Class<?> cls = attrs.getClass();
			final Field fld = cls.getField("buttonBrightness");
			if (fld != null && "float".equals(fld.getType().toString())) {
				fld.setFloat(attrs, enabled ? -1.0f : 0.0f);
				getWindow().setAttributes(attrs);
			}
		} catch (NoSuchFieldException e) {
		} catch (IllegalAccessException e) {
		}
	}

	private PowerManager.WakeLock myWakeLock;
	private boolean myWakeLockToCreate;
	private boolean myStartTimer;

	public final void createWakeLock() {
		if (myWakeLockToCreate) {
			synchronized (this) {
				if (myWakeLockToCreate) {
					myWakeLockToCreate = false;
					myWakeLock =
						((PowerManager)getSystemService(POWER_SERVICE)).
							newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "FBReader");
					myWakeLock.acquire();
				}
			}
		}
		if (myStartTimer) {
			myFBReaderApp.startTimer();
			myStartTimer = false;
		}
	}

	private final void switchWakeLock(boolean on) {
		if (on) {
			if (myWakeLock == null) {
				myWakeLockToCreate = true;
			}
		} else {
			if (myWakeLock != null) {
				synchronized (this) {
					if (myWakeLock != null) {
						myWakeLock.release();
						myWakeLock = null;
					}
				}
			}
		}
	}

	private BroadcastReceiver myBatteryInfoReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			final int level = intent.getIntExtra("level", 100);
			final ZLAndroidApplication application = (ZLAndroidApplication)getApplication();
			application.myMainWindow.setBatteryLevel(level);
			switchWakeLock(
				getZLibrary().BatteryLevelToTurnScreenOffOption.getValue() < level
			);
		}
	};

	private void setScreenBrightnessAuto() {
		final WindowManager.LayoutParams attrs = getWindow().getAttributes();
		attrs.screenBrightness = -1.0f;
		getWindow().setAttributes(attrs);
	}

	public void setScreenBrightness(int percent) {
		if (percent < 1) {
			percent = 1;
		} else if (percent > 100) {
			percent = 100;
		}
		final WindowManager.LayoutParams attrs = getWindow().getAttributes();
		attrs.screenBrightness = percent / 100.0f;
		getWindow().setAttributes(attrs);
		getZLibrary().ScreenBrightnessLevelOption().setValue(percent);
	}

	public int getScreenBrightness() {
		final int level = (int)(100 * getWindow().getAttributes().screenBrightness);
		return (level >= 0) ? level : 50;
	}
}
