package de.onyxbits.remotekeyboard;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Scanner;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.PowerManager;
import android.util.Log;

import net.wimpi.telnetd.io.BasicTerminalIO;
import net.wimpi.telnetd.io.TerminalIO;
import net.wimpi.telnetd.io.terminal.ColorHelper;
import net.wimpi.telnetd.io.toolkit.Label;
import net.wimpi.telnetd.io.toolkit.Statusbar;
import net.wimpi.telnetd.io.toolkit.Titlebar;
import net.wimpi.telnetd.net.Connection;
import net.wimpi.telnetd.net.ConnectionEvent;
import net.wimpi.telnetd.shell.Shell;

public class TelnetEditorShell implements Shell {

	public static final String TAG = "TelnetEditorShell";

	protected static TelnetEditorShell self;

	private Titlebar titleBar;
	private Statusbar statusBar;
	private Label content;
	private BasicTerminalIO m_IO;


	public TelnetEditorShell() {
	}

	@Override
	public void connectionIdle(ConnectionEvent ce) {
	}

	@Override
	public void connectionTimedOut(ConnectionEvent ce) {
	}

	@Override
	public void connectionLogoutRequest(ConnectionEvent ce) {
	}

	@Override
	public void connectionSentBreak(ConnectionEvent ce) {
	}

	/**
	 * Disconnect from remote by closing the socket
	 * 
	 * @throws IOException
	 */
	protected void disconnect() throws IOException {
		m_IO.close();
	}

	@Override
	public void run(Connection con) {
		m_IO = con.getTerminalIO();

		// Ensure the screen does not go off while we are connected.
		PowerManager pm = (PowerManager) RemoteKeyboardService.self
				.getSystemService(Context.POWER_SERVICE);
		PowerManager.WakeLock wakeLock = pm.newWakeLock(
				PowerManager.FULL_WAKE_LOCK, TAG);
		wakeLock.acquire();

		Resources res = RemoteKeyboardService.self.getResources();

		RemoteKeyboardService.self.updateNotification(con.getConnectionData()
				.getInetAddress());

		try {
			// Make the terminal window look pretty/informative.
			titleBar = new Titlebar(m_IO, "titlebar");
			titleBar.setTitleText(res.getString(R.string.terminal_title));
			titleBar.setAlignment(Titlebar.ALIGN_LEFT);
			titleBar.setForegroundColor(ColorHelper.WHITE);
			titleBar.setBackgroundColor(ColorHelper.BLUE);

			content = new Label(m_IO, "content");
			content.setLocation(0, 2);

			statusBar = new Statusbar(m_IO, "statusbar");
			statusBar.setStatusText(res.getString(R.string.terminal_statusbar));
			statusBar.setAlignment(Titlebar.ALIGN_LEFT);
			statusBar.setForegroundColor(ColorHelper.WHITE);
			statusBar.setBackgroundColor(ColorHelper.BLUE);

			showText(getWelcomeScreen());

			TextInputAction tia = new TextInputAction(RemoteKeyboardService.self);
			CtrlInputAction cia = new CtrlInputAction(RemoteKeyboardService.self);
			ActionRunner actionRunner = new ActionRunner();
			Decoder decoder = new Decoder();

			// Main loop starts here
			while (true) {
				int in = m_IO.read();
				if (in == TerminalIO.IOERROR || in == TerminalIO.HANDLED) {
					// NOTE: TerminalIO.read() internally transforms LOGOUTREEQUEST
					// into HANDLED.
					break;
				}

				switch (decoder.decode(in)) {
					case Decoder.INCOMPLETE: {
						continue;
					}
					case Decoder.PRINTABLE: {
						tia.text = decoder.getPrintable();
						actionRunner.setAction(tia);
						break;
					}
					case Decoder.FUNCTIONCODE: {
						cia.function = decoder.getFunctionCode();
						actionRunner.setAction(cia);
						break;
					}
				}
				RemoteKeyboardService.self.handler.post(actionRunner);
				actionRunner.waitResult();
			} // End of main loop.

			m_IO.eraseScreen();
			m_IO.flush();
		}
		catch (EOFException e) {
			// User disconnected disgracefully -> don't care.
		}
		catch (IOException e) {
			// Log.w(TAG, e);
		}
		finally {
			RemoteKeyboardService.self.updateNotification(null);
			wakeLock.release();
			self = null;
		}
	}

	/**
	 * Put some text in the area between title and statusbar
	 * 
	 * @param text
	 *          what to display
	 */
	public void showText(String text) {
		try {
			m_IO.eraseScreen();
			content.setText(text);
			titleBar.draw();
			content.draw();
			statusBar.draw();
			m_IO.setCursor(m_IO.getRows() - 1, m_IO.getColumns() - 1);
			m_IO.flush();
		}
		catch (IOException e) {
			Log.w(TAG, e);
		}
	}

	/**
	 * Produce a welcome screen
	 * 
	 * @return text to dump on the screen on session startup
	 */
	private String getWelcomeScreen() {
		try {
			RemoteKeyboardService myService = RemoteKeyboardService.self;
			AssetManager assetManager = myService.getResources().getAssets();
			InputStream inputStream = assetManager.open("welcomescreen.txt");
			Scanner s = new Scanner(inputStream).useDelimiter("\\A");
			return s.next();
		}
		catch (Exception exp) {
			Log.w(TAG, exp);
		}
		return "";
	}

	public static Shell createShell() {
		self = new TelnetEditorShell();
		return self;
	}

}
