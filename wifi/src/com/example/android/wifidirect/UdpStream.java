package com.example.android.wifidirect;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.InvalidParameterException;

import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import android.app.Activity;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.util.Log;

import android.media.AudioRecord;
import android.media.MediaRecorder;

/** UdpStream class sends and recv audio data through udp */

public class UdpStream extends IntentService {

	public UdpStream(String name) {
		super(name);
		// TODO Auto-generated constructor stub
	}

	public UdpStream() {
		super("UdpStream");
		// TODO Auto-generated constructor stub
	}

	static final String LOG_TAG = "UdpStream";
	//static final String AUDIO_FILE_PATH = "/sdcard/8.wav";
	private static String AUDIO_FILE_PATH = "";
	/*static final String AUDIO_NUMPKTS_PATH = "/sdcard/1.txt";*/
	static int AUDIO_PORT = 2048;
	static String HOST = "";
	
	static int STOP_PORT = 9500; 

	static final int SAMPLE_RATE = 44100; // original was 8000
	static final int SAMPLE_INTERVAL = 40; // milliseconds
	static final int SAMPLE_SIZE = 2; // bytes per sample
	//static final int BUF_SIZE = SAMPLE_INTERVAL*SAMPLE_INTERVAL*SAMPLE_SIZE*2; // original was 2
	/*static final int BUF_SIZE = android.media.AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_CONFIGURATION_MONO,
    		AudioFormat.ENCODING_PCM_8BIT);*/

	static final int BUF_SIZE = android.media.AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_CONFIGURATION_STEREO,
			AudioFormat.ENCODING_PCM_8BIT);		

	public static final String EXTRAS_GROUP_OWNER_ADDRESS = "go_host";
	public static final String EXTRAS_GROUP_OWNER_PORT = "go_port";
	//public static final String ACTION_SEND_AUDIOFILE = "com.example.android.wifidirect.SEND_FILE";
	public static final String EXTRAS_SEND = "-1";
	private static final String TAG = "UdpStream";
	public static String EXTRAS_STREAMING = "false";
	public static String EXTRAS_FILENAME = "file_name";

	private static boolean IS_RECORDING = false;
	private static boolean IS_STREAMING = true;

	private boolean first, OWNER, first_group = true;
	
	public static Socket sock = null;
	public static ServerSocket stopSocket;
	public static Socket socketClient;

	IBinder mBinder = new LocalBinder();

	public class LocalBinder extends Binder {
		public UdpStream getUdpStreamInstance() {
			return UdpStream.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Context context = getApplicationContext();
		Log.d(TAG,String.valueOf(BUF_SIZE));
		int type_stream =  intent.getExtras().getInt(EXTRAS_SEND);

		if (type_stream == 0) {
			Log.d(TAG, "send intent obtained");
			HOST = intent.getExtras().getString(EXTRAS_GROUP_OWNER_ADDRESS);
			AUDIO_PORT = intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT);
			AUDIO_FILE_PATH = "/sdcard/"+intent.getExtras().getString(EXTRAS_FILENAME);
			Log.d(TAG, " File to stream : " + AUDIO_FILE_PATH);
			SendAudio();
			

		} else if(type_stream == 1) {
			//recv file
			Log.d(TAG, "recv intent obtained");
			AUDIO_PORT = intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT);
			first = true;
			OWNER = true;
			RecvAudio();
		} else {
			Log.d(TAG, "send mic intent obtained");
			HOST = intent.getExtras().getString(EXTRAS_GROUP_OWNER_ADDRESS);
			AUDIO_PORT = intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT);

			/*SendMicAudio(true);*/
			
			if(first_group == false) {
				IS_RECORDING = true;
				Thread th1 = new Thread(new Runnable(){			

					@Override
					public synchronized void run() {
						try {
							//sock = new Socket(HOST, STOP_PORT);
							if( sock != null) {
								DataOutputStream out = new DataOutputStream(sock.getOutputStream());
								Log.d(LOG_TAG, "sending ii=1 to GO_owner");
								out.writeInt(1); // 1 is for restarting the recording
								Log.d(LOG_TAG, "sent 1 to GO_owner");
							}

						} catch (UnknownHostException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}	
				});	
				th1.start();
				
				
			}  else {
				SendMicAudio(true);
				first_group = false;
			}
		}
	}

	public void RecvAudio()
	{
		Thread thrd = new Thread(new Runnable() {
			int numpackets = 0;
			@Override
			public void run() 
			{
				Log.e(LOG_TAG, "start recv thread, thread id: "
						+ Thread.currentThread().getId());
				AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, 
						SAMPLE_RATE, AudioFormat.CHANNEL_CONFIGURATION_STEREO, 
						AudioFormat.ENCODING_PCM_16BIT, BUF_SIZE, 
						AudioTrack.MODE_STREAM);
				track.play();
				try
				{
					DatagramSocket sock = new DatagramSocket(AUDIO_PORT);
					byte[] buf = new byte[BUF_SIZE + 4];

					while(true)
					{
						if(  IS_STREAMING == false) {
							Log.d(LOG_TAG, "Packets receieved total : " + numpackets);
							IS_STREAMING = true;
						}
						DatagramPacket pack = new DatagramPacket(buf, BUF_SIZE);
						sock.receive(pack);
						Log.d(LOG_TAG, " Packet received size = " + pack.getLength() + " actual packet length : " +  BUF_SIZE);
						numpackets++;
						if(first && OWNER) {
							first = false;
							//here, as a recvier and owner, I have to start a sender thread.
							//get the packet's address and start the sender thread
							HOST = pack.getAddress().toString();
							System.out.println(HOST + " is the host");

							Thread sendThread = new Thread(new Runnable() { 
								@Override
								public void run() {
									SendMicAudio(false);

								}
							});
							sendThread.start();
							
							 Thread stopMesgThread = new Thread(new Runnable() { 
                                 @Override
                                 public synchronized void run() {
                                	 System.out.println("stop thread running");
                                         //code for recving the stop
                                         try {
                                                 stopSocket = new ServerSocket(STOP_PORT);
                                                 socketClient =  stopSocket.accept();
                                                 DataInputStream ip = new DataInputStream(socketClient.getInputStream());
                                                 System.out.println("accepting data");
                                                 while(true){
                                                	 int ii = ip.readInt();
                                                	 System.out.println("READ  : " + ii );
                                                	 
                                                	 if(ii == 2) {
                                                		 // called when stop_recording is sent from grp member
                                                		 IS_RECORDING = false;
                                                		 Log.d(LOG_TAG, "received ii=2");
                                                		 // do post send audio stuff, display received packets
                                                	 } else if (ii == 1){
                                                		 // called with start_mic is called
                                                		 IS_RECORDING = true;
                                                		 Log.d(LOG_TAG, "received ii=1");
                                                	 } else if(ii == 3) {
                                                		 IS_STREAMING = false;
                                                		 Log.d(LOG_TAG, "received ii: " + ii);
                                                	 }
                                                 }                                                
                                                 
                                         } catch (IOException e) {                                                 
                                                 e.printStackTrace();
                                         }


                                 }
                         });
						Log.d(LOG_TAG, "Stop thread will start");
                         stopMesgThread.start();
                         Log.d(LOG_TAG, "Stop thread started");                         

						}
						Log.d(LOG_TAG, "recv pack: " + pack.getLength());
						track.write(pack.getData(), 0, pack.getLength()-4);
					}
					
				}
				catch (SocketException se)
				{
					Log.e(LOG_TAG, "SocketException: " + se.toString());
				}
				catch (IOException ie)
				{
					Log.e(LOG_TAG, "IOException" + ie.toString());
				}
			} // end run
		});
		thrd.start();
	}

	public void SendAudio()
	{
		Thread thrd = new Thread(new Runnable() {
			int numpackets = 0, seq_num = 0;
			@Override
			public void run() 
			{
				Log.e(LOG_TAG, "start send thread, thread id: "
						+ Thread.currentThread().getId());
				long file_size = 0;
				int bytes_read = 0;
				int bytes_count = 0;
				File audio = new File(AUDIO_FILE_PATH);
				FileInputStream audio_stream = null;
				
				file_size = audio.length();
				byte[] buf = new byte[BUF_SIZE];
				try
				{
					InetAddress addr = InetAddress.getByName(HOST);
					DatagramSocket sock = new DatagramSocket();
					audio_stream = new FileInputStream(audio);
				
					while(bytes_count < file_size)
					{
						bytes_read = audio_stream.read(buf, 0, BUF_SIZE);
						DatagramPacket pack = new DatagramPacket(buf, bytes_read,
								addr, AUDIO_PORT);
						numpackets++;
						sock.send(pack);
						bytes_count += bytes_read;
						//Log.d(LOG_TAG, "bytes_count : " + bytes_count);
						Thread.sleep(SAMPLE_INTERVAL, 0);
					}
					System.out.println("Packets sent : " + numpackets);
					stopStreaming();
					
				}
				catch (InterruptedException ie)
				{
					Log.e(LOG_TAG, "InterruptedException");
				}
				catch (FileNotFoundException fnfe)
				{
					fnfe.printStackTrace();
					Log.e(LOG_TAG, "FileNotFoundException");
				}
				catch (SocketException se)
				{
					Log.e(LOG_TAG, "SocketException");
				}
				catch (UnknownHostException uhe)
				{
					Log.e(LOG_TAG, "UnknownHostException");
				}
				catch (IOException ie)
				{
					Log.e(LOG_TAG, "IOException");
				}
			} // end run
		});
		thrd.start();
	}

	public void SendMicAudio(boolean group_hack) {

		if(group_hack) {
			//this is the member side, if he initiates the call, 
			//owner can sstart speaking also.
			//call recv audio.
			Thread recvThread = new Thread(new Runnable() { 


				@Override
				public void run() {
					
					RecvAudio();

				}
			});
			recvThread.start();
		}

		final int buffsize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_CONFIGURATION_STEREO,
				AudioFormat.ENCODING_PCM_16BIT);
		final AudioRecord audio_recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, 
				AudioFormat.CHANNEL_CONFIGURATION_STEREO, AudioFormat.ENCODING_PCM_16BIT, buffsize);

		audio_recorder.startRecording();
		IS_RECORDING = true;

		Thread recordThread = new Thread(new Runnable() {    		
			@Override
			public void run() {
				int bytes_read = 0;
				int bytes_count = 0;
				byte[] buf = new byte[buffsize];

				try {
					InetAddress addr;
					if(OWNER) {
						addr = InetAddress.getByName(HOST.substring(1));
						System.out.println("addr is " + addr);
					} else {
						addr = InetAddress.getByName(HOST);
					}

					DatagramSocket sock = new DatagramSocket();

					while(true) {
						// TODO Reset to default values
						//Log.d(LOG_TAG, "I will start recording now");

						while(IS_RECORDING) {
							bytes_read = audio_recorder.read(buf, 0, buffsize);
							DatagramPacket pack = new DatagramPacket(buf, bytes_read, addr, AUDIO_PORT);
							sock.send(pack);
							bytes_count += bytes_read;
							//Log.d(LOG_TAG, "bytes_count : " + bytes_count);
							Thread.sleep(SAMPLE_INTERVAL, 0);
						}
						//this is commented because we do not want to destroy objects or stop thix thread
						/*if(IS_RECORDING == false) {
							if(audio_recorder != null) {
								audio_recorder.stop();
								audio_recorder.release();
							}
						}*/
					}

				} catch (InterruptedException ie) {

					Log.e(LOG_TAG, "InterruptedException");

				} catch (SocketException se) {

					Log.e(LOG_TAG, "SocketException");

				} catch (UnknownHostException uhe) {

					Log.e(LOG_TAG, "UnknownHostException");
					uhe.printStackTrace();

				} catch (IOException ie) {

					Log.e(LOG_TAG, "IOException");

				}
			}

		});

		recordThread.start();
	}

	/*should not be used by owner*/
	public void stopRecording() {
		IS_RECORDING = false; 
		Thread th = new Thread(new Runnable(){			

			@Override
			public synchronized void run() {
				try {
					if(sock == null)
						sock = new Socket(HOST, STOP_PORT);

					DataOutputStream out = new DataOutputStream(sock.getOutputStream());
					out.writeInt(2); // 2 is for stop recording
					Log.d(LOG_TAG, "sent ii = 2 to stop the recording");

				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}

			}	

		});	
		th.start();
	}
	
	public void stopStreaming() {
		
		Thread th = new Thread(new Runnable(){			

			@Override
			public synchronized void run() {
				try {
					if(sock == null)
						sock = new Socket(HOST, STOP_PORT);

					DataOutputStream out = new DataOutputStream(sock.getOutputStream());
					out.writeInt(3); // 3 is for stop streaming

				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}

			}	

		});	
		th.start();
		
	}

	/*public void SendMicAudio()
    {
        Thread thrd = new Thread(new Runnable() {
			@Override
            public void run() 
            {
                Log.e(LOG_TAG, "start SendMicAudio thread, thread id: "
                    + Thread.currentThread().getId());
                AudioRecord audio_recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                            AudioFormat.CHANNEL_CONFIGURATION_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            AudioRecord.getMinBufferSize(SAMPLE_RATE,
                                    AudioFormat.CHANNEL_CONFIGURATION_MONO,
                                    AudioFormat.ENCODING_PCM_16BIT) * 10);
                int bytes_read = 0;
                int bytes_count = 0;
                byte[] buf = new byte[BUF_SIZE];
                try
                {
                    InetAddress addr = InetAddress.getByName(HOST);
                    DatagramSocket sock = new DatagramSocket();

                    while(true)
                    {
                        bytes_read = audio_recorder.read(buf, 0, BUF_SIZE);
                        DatagramPacket pack = new DatagramPacket(buf, bytes_read,
                                addr, AUDIO_PORT);
                        sock.send(pack);
                        bytes_count += bytes_read;
                        Log.d(LOG_TAG, "bytes_count : " + bytes_count);
                        Thread.sleep(SAMPLE_INTERVAL, 0);
                    }
                }
                catch (InterruptedException ie)
                {
                    Log.e(LOG_TAG, "InterruptedException");
                }
//                catch (FileNotFoundException fnfe)
//                {
//                    Log.e(LOG_TAG, "FileNotFoundException");
//                }
                catch (SocketException se)
                {
                    Log.e(LOG_TAG, "SocketException");
                }
                catch (UnknownHostException uhe)
                {
                    Log.e(LOG_TAG, "UnknownHostException");
                }
                catch (IOException ie)
                {
                    Log.e(LOG_TAG, "IOException");
                }
            } // end run
        });
        thrd.start();
    }*/


}
