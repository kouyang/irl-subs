package kouyang.irlsubs.audio;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

public class MainAudio extends Thread {
	// Constants
	// Determine Audio Format (44.1 kHz, 16-bit signed, stereo)
	final static float fSampleRate = 48000.0F; // 16 kHz Sample Rate
	final static int iBytesPerSample = 2; // 16-bit sample
	final static int channels       = 2;  // Stereo Recording
	final static int frameSize = channels * iBytesPerSample;
	
	final static int CHUNK_SIZE = 1024;

	private AudioFormat m_format;
	private PipedInputStream[] m_inStreams;
	private PipedOutputStream[] m_outStreams;
	private TargetDataLine microphone;
	
	private boolean m_stopped;
	
	public MainAudio(int numStreams) {
		m_format = new AudioFormat(
				AudioFormat.Encoding.PCM_SIGNED,
				fSampleRate, iBytesPerSample * 8, channels, frameSize, fSampleRate, false);
		m_inStreams = new PipedInputStream[numStreams];
		m_outStreams = new PipedOutputStream[numStreams];
		
		for(int i = 0; i < m_inStreams.length; i++) {
			try {
				m_inStreams[i] = new PipedInputStream();
				m_outStreams[i] = new PipedOutputStream(m_inStreams[i]);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		
		m_stopped = false;
	}
	
	public PipedInputStream[] initialize() {
		try {
			microphone = AudioSystem.getTargetDataLine(m_format);
	
			DataLine.Info info = new DataLine.Info(TargetDataLine.class, m_format);
			microphone = (TargetDataLine) AudioSystem.getLine(info);
			microphone.open(m_format);
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		return m_inStreams;
	}
	
	public void quit() {
		m_stopped = true;
	}
	
	public AudioFormat getFormat() {
		return m_format;
	}
	
	@Override
	public void run() {
		int numBytesRead;
		
		byte[] data = new byte[microphone.getBufferSize() / 5];
		
		microphone.start();
		try {
			while (!m_stopped) {
				numBytesRead = microphone.read(data, 0, CHUNK_SIZE);
				// write the microphone data to a stream for use later
				for(PipedOutputStream p : m_outStreams) {
					p.write(data, 0, numBytesRead); 
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		microphone.close();
	}
	
	public static void main(String[] args) {
		MainAudio m = new MainAudio(1);
		
		PipedInputStream[] streams = m.initialize();
		
		//OffsetCalc oc = new OffsetCalc(0.0762, 1.0, streams[0]);
		SpeechRec sr = new SpeechRec(5.0, streams[0]);
		
		m.start();
		sr.start();
		/* try {
			Thread.sleep(5000);
			
			AudioInputStream ais = new AudioInputStream(streams[0], new AudioFormat(
					AudioFormat.Encoding.PCM_SIGNED,
					fSampleRate * 2, iBytesPerSample * 8, 1, frameSize / 2, fSampleRate * 2, false), (int)(frameSize / 2 * fSampleRate * 5));
			AudioSystem.write(ais, Type.WAVE, new File("sound.wav"));
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			m.quit();
		} */
		
		System.out.println("Ending...");
	}

}
