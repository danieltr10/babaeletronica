package br.usp.babaeletronicapcs3614;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class AudioCall {


	private static final int SAMPLE_RATE = 8000;
	private static final int SAMPLE_INTERVAL = 5;
	private static final int SAMPLE_SIZE = 2;
	private static final int BUF_SIZE = SAMPLE_INTERVAL * SAMPLE_INTERVAL * SAMPLE_SIZE * 2;
	private InetAddress address;
	private int port = 50000;
	private boolean mic = false;
	private boolean speakers = false;
	private String type = "";
	
	public AudioCall(InetAddress address, String type) {
		this.type = type;
		this.address = address;
	}
	
	public void startCall() {

		if (type.equals(MainActivity.BABY)) {
			startMic();
		} else {
			startSpeakers();
		}
	}
	
	public void endCall() {
		
		muteMic();
		muteSpeakers();
	}
	
	public void muteMic() {
		
		mic = false;
	}
	
	public void muteSpeakers() {
		
		speakers = false;
	}
	
	public void startMic() {
		// Creates the thread for capturing and transmitting audio
		mic = true;
		Thread thread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				AudioRecord audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
						AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
						AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)*10);
				int bytes_read = 0;
				int bytes_sent = 0;
				byte[] buf = new byte[BUF_SIZE];
				try {
					// Create a socket and start recording
					DatagramSocket socket = new DatagramSocket();
					audioRecorder.startRecording();
					while(mic) {
						// Capture audio from the mic and transmit it
						bytes_read = audioRecorder.read(buf, 0, BUF_SIZE);
						DatagramPacket packet = new DatagramPacket(buf, bytes_read, address, port);
						socket.send(packet);
						bytes_sent += bytes_read;
						Thread.sleep(SAMPLE_INTERVAL, 0);
					}
					// Stop recording and release resources
					audioRecorder.stop();
					audioRecorder.release();
					socket.disconnect();
					socket.close();
					mic = false;
					return;
				}
				catch(Exception e) {
					mic = false;
					e.printStackTrace();
				}
			}
		});
		thread.start();
	}
	
	public void startSpeakers() {
		// Creates the thread for receiving and playing back audio
		if(!speakers) {
			
			speakers = true;
			Thread receiveThread = new Thread(new Runnable() {
				
				@Override
				public void run() {
					// Create an instance of AudioTrack, used for playing back audio
					AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
							AudioFormat.ENCODING_PCM_16BIT, BUF_SIZE, AudioTrack.MODE_STREAM);
					track.play();
					try {
						// Define a socket to receive the audio
						DatagramSocket socket = new DatagramSocket(port);
						byte[] buf = new byte[BUF_SIZE];
						while(speakers) {
							// Play back the audio received from packets
							DatagramPacket packet = new DatagramPacket(buf, BUF_SIZE);
							socket.receive(packet);
							track.write(packet.getData(), 0, BUF_SIZE);
						}
						// Stop playing back and release resources
						socket.disconnect();
						socket.close();
						track.stop();
						track.flush();
						track.release();
						speakers = false;
						return;
					}
					catch(Exception e) {
						speakers = false;
						e.printStackTrace();
					}
				}
			});
			receiveThread.start();
		}
	}
}
