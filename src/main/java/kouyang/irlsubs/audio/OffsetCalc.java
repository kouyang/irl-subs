package kouyang.irlsubs.audio;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javafx.beans.property.DoubleProperty;
import javafx.application.Platform;



import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.*;

public class OffsetCalc extends Thread {
	
	final static int CHUNK_SIZE = 1024;
	
	final static double SOUND_SPEED = 342.2; // SI Units
	
	// Fields
	private double m_micDistance;
	private double m_refresh;
	
	private double m_offset;
	
	private boolean m_stopped;

	PipedInputStream m_audioData;
	
	DoubleProperty m_doubleProp;


	public OffsetCalc(double micDistance, double refresh, PipedInputStream audioData, DoubleProperty doubleProp) {
		m_refresh = refresh;
		m_micDistance = micDistance;
		m_audioData = audioData;
		m_offset = 0;
		
		m_doubleProp = doubleProp;
		
		m_stopped = false;
	}
	
	public void quit() {
		m_stopped = true;
	}

	@Override
	public void run() {
		while(!m_stopped) {
			
			ByteArrayOutputStream out = new ByteArrayOutputStream();
	
			byte[] data = new byte[CHUNK_SIZE];
			int bytesRead = 0;
			int numBytesRead = 0;
			
			try {
				while (bytesRead < (int)(m_refresh * MainAudio.fSampleRate * MainAudio.frameSize)) {
					numBytesRead = m_audioData.read(data, 0, CHUNK_SIZE);
					bytesRead += numBytesRead;
					// write the microphone data to a stream for use later
					out.write(data, 0, numBytesRead);
					if (m_stopped) return;
				}
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			byte[] soundBytes = out.toByteArray();
			short[] soundSamples = new short[soundBytes.length / 2];
	
			ByteBuffer.wrap(soundBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(soundSamples);
	
			double[] leftSamples = new double[soundSamples.length + 8704];
			double[] rightSamples = new double[soundSamples.length + 8704];
	
			for(int i = 0; i < soundSamples.length / 2; i++) {
				leftSamples[i] = (double)soundSamples[2 * i];
				rightSamples[i] = (double)soundSamples[2 * i + 1];
			}
	
			// Start Fourier Transform
			FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
	
			Complex[] leftTransform = fft.transform(leftSamples, TransformType.FORWARD);
			Complex[] rightTransform = fft.transform(rightSamples, TransformType.FORWARD);
	
			Complex[] leftcorrelateTransform = new Complex[leftTransform.length];
	
			for(int i = 0; i < leftTransform.length; i++) {
				leftcorrelateTransform[i] = leftTransform[i].conjugate().multiply(rightTransform[i]);
			}
	
			Complex[] leftcorrelationComplex = fft.transform(leftcorrelateTransform, TransformType.INVERSE);
	
			double[] leftcorrelation = new double[leftcorrelationComplex.length];
	
			for(int i = 0; i < leftcorrelation.length; i++) {
				leftcorrelation[i] = leftcorrelationComplex[i].abs();
			}
	
			int leftmax = 0;
			double leftbest = 0;
	
			for(int i = 0; i < leftcorrelation.length; i++) {
				if (leftcorrelation[i] > leftbest) {
					leftbest = leftcorrelation[i];
					leftmax = i;
				}
			}
	
			if (leftmax > leftcorrelation.length / 2) leftmax -= leftcorrelation.length;
	
		    	m_offset = (double)leftmax / (double)MainAudio.fSampleRate;
		    	double ratio = m_offset * SOUND_SPEED / m_micDistance;
		    	if (ratio > 1) ratio = 1;
		    	if (ratio < -1) ratio = -1;
		    	m_offset = Math.toDegrees(Math.asin(ratio));
		    	if (m_offset < -40) m_offset = -40;
		    	if (m_offset > 40) m_offset = 40;
		    	
		    	Platform.runLater(new Runnable() {
		    		@Override
		    		public void run() {
		    			m_doubleProp.set(-1 * m_offset);
		    		}
		    	});
		}
	}
}
