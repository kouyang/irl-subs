package kouyang.irlsubs.audio;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javafx.application.Platform;
import javafx.beans.property.StringProperty;

import javax.net.ssl.HttpsURLConnection;
import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.json.JSONException;
import org.json.JSONObject;

public class SpeechRec extends Thread {
	/** OAuth URL. */
	private static final String OAUTH_URL = "https://api.att.com/oauth/v4/token";

	/** Speech to text URL. */
	private static final String SPEECH_TO_TEXT_URL = "https://api.att.com/speech/v3/speechToText";

	/** Translation URL. */
	private static final String TRANSLATION_URL = "https://translate.yandex.net/api/v1.5/tr.json/translate";

	/** Default language (no translation). */
	private static final String DEFAULT_LANGUAGE = "en";

	// Fields
	private double m_refresh;
	private boolean m_stopped;
	private ByteArrayOutputStream m_byteArrayStream;
	private PipedInputStream m_audioData;
	private String m_text;
	
	final static float fSampleRate = 48000.0F; // 16 kHz Sample Rate
	final static int iBytesPerSample = 2; // 16-bit sample
	final static int channels       = 2;  // Stereo Recording
	final static int frameSize = channels * iBytesPerSample;
	
	private String authToken;
	
	private Lock lock;

	private StringProperty subtitleProp;
	private String lang = DEFAULT_LANGUAGE;

	public SpeechRec(double refresh, PipedInputStream audioData, StringProperty subtitleProp) {
		m_refresh = refresh;
		m_audioData = audioData;
		this.subtitleProp = subtitleProp;
		
		m_stopped = false;
		m_text = "";
		lock = new ReentrantLock();
	}

	public void quit() {
		m_stopped = true;
	}
	
	@Override
	public void run() {
		while(!m_stopped) {
			try {
				m_byteArrayStream = new ByteArrayOutputStream();
				// get audio stream
				AudioInputStream ais = new AudioInputStream(m_audioData, new AudioFormat(
						AudioFormat.Encoding.PCM_SIGNED,
						fSampleRate * 2, iBytesPerSample * 8, 1, frameSize / 2, fSampleRate * 2, false), (int)(frameSize / 2 * fSampleRate * m_refresh));
				AudioSystem.write(ais, Type.WAVE, m_byteArrayStream);

				if (authToken == null)
					authToken = getOAuth();
				if (authToken == null) continue;
				lock.lock();
			    try {
			    	m_text = getTextFromSpeech(authToken);
			    	if (!lang.equals(DEFAULT_LANGUAGE))
				    	m_text = translateText(lang);
			    	setSubtitle(m_text);
			    } finally {
			    	lock.unlock();
			    }
			} catch (Exception e) {
				e.printStackTrace();
	//			System.exit(1);
			}
		}
	}

	private void setSubtitle(final String s) {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				subtitleProp.setValue(s);
			}
		});
	}
	
	public void setLanguage(String language) {
		this.lang = language;
	}
	
	public String getText() {
		lock.lock();
		String ret;
	    try {
	    	ret = m_text;
	    } finally {
	    	lock.unlock();
	    }
	    return ret;
	}

	// make an oauth request
	private String getOAuth() throws Exception {
	    URL obj = new URL(OAUTH_URL);
		
		HttpsURLConnection con;
		con = (HttpsURLConnection) obj.openConnection();
		
		//add request header
		con.setRequestMethod("POST");
		con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		con.setRequestProperty("Accept", "application/json");
		
		String data = "client_id=rj2fdxuciodyjy1qkioystmjbsjpgdnl&client_secret=0vbugp96se0mww44uc7xvuhjs2qgwohf&grant_type=client_credentials&scope=SPEECH";

		// Send post request
		con.setDoOutput(true);
		DataOutputStream wr = new DataOutputStream(con.getOutputStream());
		wr.writeBytes(data);
		wr.flush();
		wr.close();
 
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
 
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		
		JSONObject json = new JSONObject(response.toString());
		System.out.println("Access Code is: " + json.getString("access_token"));
		return json.getString("access_token");
	}

	// make a request for text to att
	private String getTextFromSpeech(String authToken) throws Exception {
	    URL url = new URL(SPEECH_TO_TEXT_URL);
		
		HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

		// add request header
		con.setRequestMethod("POST");
		con.setRequestProperty("Authorization", "Bearer " + authToken);
		con.setRequestProperty("Accept", "application/json");
		con.setRequestProperty("Content-Type", "audio/wav");
		con.setRequestProperty("Transfer-Encoding", "chunked");

		byte[] data = m_byteArrayStream.toByteArray();

		// Send post request
		con.setDoOutput(true);
		DataOutputStream wr = new DataOutputStream(con.getOutputStream());
		wr.write(data);
		wr.flush();
		wr.close();

		BufferedReader in = null;
		
		try {
			in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		} catch(Exception e) {
			System.out.println("Exception!");
			return null;
		}
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();

		JSONObject json = new JSONObject(response.toString());
//		System.out.println(json.toString());
		try {
			String result = json.getJSONObject("Recognition").getJSONArray("NBest").getJSONObject(0).getString("ResultText");
			System.out.println(result);
			return result;
		} catch (JSONException e) {
			return "";
		}
	}
	
	private String translateText(String lang) throws Exception {
	    URL obj = new URL(TRANSLATION_URL);
		
		HttpsURLConnection con;
		con = (HttpsURLConnection) obj.openConnection();
		
		//add request header
		con.setRequestMethod("POST");

		String text = URLEncoder.encode(m_text, "UTF-8");		
		
		String data = "key=trnsl.1.1.20150412T085953Z.f3946b13feb0ece5.44c6a33ca802483d24ded334d8ff52b463078714"
				+ "&lang=en-" + lang + "&text=" + text;
		
		// Send post request
		con.setDoOutput(true);
		DataOutputStream wr = new DataOutputStream(con.getOutputStream());
		wr.writeBytes(data);
		wr.flush();
		wr.close();
 
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
 
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
				 
		JSONObject json = new JSONObject(response.toString());
//		System.out.println(json.toString());
		try {
			String result = json.getJSONArray("text").getString(0);
			System.out.println(result);
			return result;
		} catch (Exception e) {
			return "";
		}
	}	


}
