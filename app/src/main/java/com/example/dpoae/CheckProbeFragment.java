package com.example.dpoae;

import static com.example.dpoae.Constants.fullrec;
import static com.example.dpoae.Constants.volumeSetting;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AutomaticGainControl;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.MenuPopupWindow;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class CheckProbeFragment extends Fragment {

    TextView textView = null;
    SurfaceView surfaceView;
    SurfaceHolder holder;
    Canvas canvas;
    Paint paint;
    View view;
    MaterialAutoCompleteTextView bufferSpinner;
    int phaseIndex;
    boolean isDrawing;
    int freqIndex;
    AudioStreamer sp;
    private Listener listener;
    private short[] accumulatedSamples =
            new short[Constants.samplingRate * Constants.CONSTANT_TONE_LENGTH_IN_SECONDS];

    private void draw()  //This gets called when the Draw button is clicked.
    {
        //setContentView(R.layout.activity_main);
        //textView = (TextView) findViewById(R.id.textView);
        //textView.setText("Press the Draw button!");
        paint.setColor(Color.GREEN);
        paint.setStrokeWidth(5);
        int ww = view.getWidth();
        int hh = view.getHeight();
        surfaceView.getHolder().setFixedSize(ww - 200, hh - 200);

        int phaseSamples = 50;
        //canvas = holder.lockCanvas();
        canvas = surfaceView.getHolder().lockCanvas();
        if (canvas == null) {
            return;
        }

        float a, w, h, x, y, phase;
        w = canvas.getWidth();
        h = canvas.getHeight();
        canvas.drawRGB(32, 32, 255);    //Clear the canvas to light blue
        canvas.drawLine(0, h / 2, (int) w, h / 2, paint);//Draw a line from left to right as the y=0 line.
        phase = (float) (phaseIndex++ / (phaseSamples - 1.0f) * 2 * Math.PI);
        x = 0;
        while (x < w) {
            a = (x / w) * (float) (2.0 * Math.PI);
            y = (h / 2.0f) - ((float) Math.sin(a + phase) * (h / (float) 2.2));

            canvas.drawPoint(x, y, paint);

            x++;
        }

        holder.unlockCanvasAndPost(canvas);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //container.removeAllViews();
        view = inflater.inflate(
                R.layout.probe_check,
                container,
                false);
        ((MainActivity) getActivity()).getSupportActionBar().hide();
        textView = view.findViewById(R.id.textView);
        textView.setText("Press the Draw button");

        bufferSpinner = view.findViewById(R.id.bufferSpinner);
        List<String> spinnerArray =  new ArrayList<String>();
        for (String s:new String[] {"4096", "8192", "16384", "32768", "65536" }) {
            spinnerArray.add(s);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                getActivity(), android.R.layout.simple_spinner_item, spinnerArray);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        bufferSpinner.setAdapter(adapter);
        bufferSpinner.setText("16384", false);

        surfaceView = view.findViewById(R.id.surfaceView);
        //surfaceView.postInvalidate();
        holder = surfaceView.getHolder();
        holder.getSurface();
        paint = new Paint();
        initView(view);
        final Button buttonDraw = view.findViewById(R.id.buttonDraw);
        buttonDraw.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                isDrawing = !isDrawing;
                if (isDrawing) {
                    //draw();
                    // Start playing frequencies on the speakers
                    int freq = Constants.octaves.get(freqIndex);
                    int f1 = Constants.freqLookup.get(freq);
                    int f2 = freq;
                    textView.setText(String.format("{0}, {1}", f1, f2));


                    short[] pulse;
                    float vol3a = Constants.vol3Lookup.get(f1) * Constants.CONSTANT_VOLUME_SETTING / 100.0f;
                    float vol3b = Constants.vol3Lookup.get(f2) * Constants.CONSTANT_VOLUME_SETTING / 100.0f;

                    pulse = SignalGenerator.sine2speaker(f1, f2,
                            Constants.samplingRate,
                            Constants.CONSTANT_TONE_LENGTH_IN_SECONDS * Constants.samplingRate,
                            vol3a,
                            vol3b);

                    float vol1 = Constants.vol1Lookup.get(f2);

                    FragmentActivity activity = getActivity();
                    if (activity == null) return;
                    sp = new AudioStreamer(activity, pulse, Constants.samplingRate * Constants.CONSTANT_TONE_LENGTH_IN_SECONDS * 2,
                            Constants.samplingRate, AudioManager.STREAM_SYSTEM, vol1, true);

                    sp.play(-1);

                    // Start recording the microphone
                    String bls = bufferSpinner.getText().toString();
                    bls = bls.length()==0?"0":bls;
                    int bl = Integer.parseInt(bls);
                    listener = new Listener(bl);
                    listener.start();
                } else {
                    if (sp != null) {
                        sp.stopit();
                        sp = null;
                    }

                    if (listener != null) {
                        listener.stopit();
                        listener = null;
                    }

                    freqIndex++;
                    if (freqIndex > 3) freqIndex = 0;
                }
            }
        });

        surfaceView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                //draw();
            }
        });

        view.setVisibility(View.VISIBLE);
        int w = view.getWidth();
        int h = view.getHeight();
        surfaceView.getHolder().setFixedSize(w - 100, h - 100);

        surfaceView.setVisibility(View.VISIBLE);
        surfaceView.bringToFront();
        surfaceView.setZOrderOnTop(true);

//        new DrawTask().execute();
        new Thread(new Runnable() {
            @Override
            public void run() {
                // do your stuff
                while (true) {
                    if (isDrawing) {
                        FragmentActivity activity = getActivity();
                        if (activity != null) {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    drawFft();
                                }
                            });
                        }
                    }
                    try {
                        TextInputEditText st=(TextInputEditText)getActivity().findViewById(R.id.sleepTime);
                        int sti = st.getText().toString().length() == 0 ? 0 : Integer.parseInt(st.getText().toString());
                        Thread.sleep(sti);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }).start();

        return view;
    }

    private void drawFft() {
        if (listener == null) return;

        // Read the data
        short[] b = listener.getAudio();

        // Draw it.
        Paint text = new Paint();
        text.setColor(Color.BLACK);
        text.setTextSize(60);
        paint.setColor(Color.GREEN);
        paint.setStrokeWidth(5);
        int ww = view.getWidth();
        int hh = view.getHeight();
        surfaceView.getHolder().setFixedSize(ww - 200, hh - 200);

        canvas = surfaceView.getHolder().lockCanvas();
        if (canvas == null) {
            return;
        }

        float a, w, h, phase;
        w = canvas.getWidth();
        h = canvas.getHeight();
        canvas.drawRGB(32, 32, 255);    //Clear the canvas to light blue
        Paint axis = new Paint();
        axis.setColor(Color.WHITE);
        axis.setStrokeWidth(5);
        canvas.drawLine(0, h / 1.1f, (int) w, h / 1.1f, axis);//Draw a line from left to right as the y=0 line.

        double[] avbuffer = new double[b.length];
        for (int i = 0; i < b.length; i++) avbuffer[i] = b[i];

        int f1 = Constants.octaves.get(freqIndex);
        int f2 = Constants.freqLookup.get(f1);
        int fOae = Constants.oaeLookup.get(f1);

        // get the SNR.
        // TODO: To mimic real measurement, create short[288K] and add the current samples to the end of this array.
        // copy the last part to the beginning.
        // or example, when AccSamp.Len is 288K, and b.Len is 16K
        //   copy from 16K to end to index 0
        System.arraycopy(
                this.accumulatedSamples,
                b.length,
                this.accumulatedSamples,
                0,
                this.accumulatedSamples.length-b.length);

        // Append the last read buffer to AccSamples
        System.arraycopy(
                b,
                0,
                this.accumulatedSamples,
                this.accumulatedSamples.length-b.length,
                b.length);
        Constants.done=new boolean[4];
        Signal.work(null, this.accumulatedSamples,f2,f1, freqIndex);
        float snr = Constants.graphData.get(freqIndex).getY();


        Switch s1 = view.findViewById(R.id.customFftSwitch);

        double[] spec =
                s1.isChecked() ?
                        new FFT().Fft(avbuffer) :
                        MeasureFragment.fftnative(avbuffer, avbuffer.length);

        CheckBox zc = view.findViewById(R.id.zoomCheck);
        boolean zoom = zc.isChecked();

        Switch s2 = view.findViewById(R.id.log10Switch);
        if(s2.isChecked())
            for (int i = 0; i < spec.length; i++)
                spec[i]=Math.log10(spec[i]);

        // freq = i * Constants.samplingRate / b.length
        // draw from 0Hz to 6KHz
        int i6khz=(int)(6000.0/Constants.samplingRate * b.length);
        int xStart=0;
        int xEnd=i6khz;
        int[] freqMarkers=new int[]{f1,f2,fOae};
        paint.setStrokeWidth(5);
        if(zoom)
        {
            xStart = (int)((fOae-100.0f)/Constants.samplingRate * b.length);
            xEnd = (int)((fOae+100.0f)/Constants.samplingRate * b.length);
            freqMarkers=new int[]{fOae};
            paint.setStrokeWidth(20);
        }
        int xLength=xEnd-xStart;

        // draw lines at f1, f2 and oae frequencies:
        axis.setColor(Color.RED);
        axis.setStrokeWidth(2);
        for(int ff : freqMarkers) {
            int df=zoom?100:ff;
            int x0 = (int) ((float) (df) / xLength * w);
            int i0=(int)((float)(x0)/Constants.samplingRate * b.length);
            canvas.drawLine(i0, 0, i0, h, axis);
        }

        // compute max magnitude
        double max = 0;
        for (int xx = xStart; xx < xEnd; xx++)
            if (spec[xx] > max) max = spec[xx];

        for (int xx = xStart; xx < xEnd; xx++) {
            int y = (int) ((h / 1.1f) -  h / 1.1f * spec[xx] / max);

            int x = (int) (((float)xx-xStart) / xLength * w);

            canvas.drawPoint(x, y, paint);
        }

        text.setColor(Color.WHITE);
        text.setTextSize(120);
        canvas.drawText(
                String.format("%d",(int)snr),
                10,
                100,
                text);

        text.setColor(Color.BLACK);
        text.setTextSize(60);
        canvas.drawText(
                String.format("%d, %d, %d",f1,f2,fOae),
                100,
                100,
                text);

        holder.unlockCanvasAndPost(canvas);
    }

    public void checkAction(int c, boolean isChecked) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getActivity()).edit();
        Log.e("asdf", "checkaction " + c + "," + isChecked);
        if (c == 1) {
            Log.e("asdf", "set check0 to " + isChecked);
            editor.putBoolean("check0", isChecked);
            Constants.freqs[0] = isChecked;
        } else if (c == 2) {
            editor.putBoolean("check1", isChecked);
            Constants.freqs[1] = isChecked;
        } else if (c == 3) {
            editor.putBoolean("check2", isChecked);
            Constants.freqs[2] = isChecked;
        } else if (c == 4) {
            editor.putBoolean("check3", isChecked);
            Constants.freqs[3] = isChecked;
        }
        for (int i = 0; i < Constants.freqs.length; i++) {
            Log.e("asdf", i + ":" + Constants.freqs[i]);
        }
        editor.commit();
    }

    public void initView(View view) {
        Log.e("asdf", "SET CHECKED ");
    }

    @Override
    public void onResume() {
        super.onResume();
        Constants.CurrentFragment = this;
        Constants.CheckProbeFragment = this;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Constants.CurrentFragment = this;
        Constants.CheckProbeFragment = this;
    }

    private class Listener extends Thread {
        private AudioRecord rec;
        int minbuffersize;
        private boolean recording;
        private ReentrantLock audioBufferLock;
        short[] buffer;
        int tail;

        public Listener(int bufLen) {
            minbuffersize = AudioRecord.getMinBufferSize(
                    Constants.samplingRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);

            rec = new AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT,
                    Constants.samplingRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minbuffersize);

            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl agc = AutomaticGainControl.create(rec.getAudioSessionId());
                agc.setEnabled(false);
            }

            this.tail = 0;
            this.buffer = new short[bufLen];
            this.audioBufferLock = new ReentrantLock();
        }

        public short[] getAudio() {
            this.audioBufferLock.lock();
            short[] b;
            try {
                if (this.tail == 0) {
                    return this.buffer.clone();
                }

                b = new short[this.buffer.length];
                int start = this.tail;
                System.arraycopy(this.buffer, start, b, 0, this.buffer.length - start);
                System.arraycopy(this.buffer, 0, b, this.buffer.length - start, start);
            } finally {
                this.audioBufferLock.unlock();
            }
            return b;
        }

        public void run() {
            int bytesread;
            rec.startRecording();
            recording = true;
            short[] temp = new short[minbuffersize];
            while (recording) {
                bytesread = rec.read(temp, 0, minbuffersize);

                this.audioBufferLock.lock();
                try {
                    for (int i = 0; i < bytesread; i++) {
                        this.buffer[this.tail] = temp[i];
                        this.tail = (this.tail + 1) % this.buffer.length;
                    }
                } finally {
                    this.audioBufferLock.unlock();
                }
            }
        }

        public void stopit() {
            if (rec.getState() == AudioRecord.STATE_INITIALIZED ||
                    rec.getState() == AudioRecord.RECORDSTATE_RECORDING) {
                rec.stop();
            }

            recording = false;

            rec.release();
        }
    }

    /*    private class DrawTask extends AsyncTask<Void, Void, Void> {
        public CheckProbeFragment fragment;

        public DrawTask(CheckProbeFragment fragment)
        {
            super();
            this.fragment=fragment;
        }

        @Override
        protected Void doInBackground(Void... params) {
            return null;
        }
    }
 */

    private class FFT {

        public double[] Fft(double[] in) {
            Complex[] out = new Complex[in.length];
            for (int i = 0; i < in.length; i++) {
                out[i] = new Complex(in[i], (double)0.0);
            }

            // fft(out);
            fft2(out);
            for (int i = 0; i < in.length; i++) {
                in[i] = Math.sqrt(out[i].re * out[i].re + out[i].im * out[i].im);
            }

            return in;
        }

        public  void fft2(Complex[] buffer)
        {
            for (int i = 1; i < buffer.length; i++)
            {
                int j = BitReverse(i, buffer.length);
                if (j > i) {
                    // (buffer[j], buffer[i]) =(buffer[i], buffer[j]);
                    Complex tmp=buffer[j];
                    buffer[j]=buffer[i];
                    buffer[i]=tmp;
                }
            }

            for (int i = 1; i <= buffer.length / 2; i *= 2)
            {
                double mult1 = -Math.PI / i;
                for (int j = 0; j < buffer.length; j += (i * 2))
                {
                    for (int k = 0; k < i; k++)
                    {
                        int evenI = j + k;
                        int oddI = j + k + i;
                        Complex temp = new Complex(Math.cos(mult1 * k), Math.sin(mult1 * k));
                        //temp *= buffer[oddI];
                        temp = new Complex(temp.re*buffer[oddI].re-temp.im*buffer[oddI].im, temp.re*buffer[oddI].im+temp.im*buffer[oddI].re);
                        //buffer[oddI] = buffer[evenI] - temp;
                        buffer[oddI] = new Complex(buffer[evenI].re - temp.re,buffer[evenI].im - temp.im);
                        // buffer[evenI] += temp;
                        buffer[evenI] = new Complex(buffer[evenI].re + temp.re,buffer[evenI].im + temp.im);

                    }
                }
            }
        }

        private int BitReverse(int value, int maxValue)
        {
            int maxBitCount = (int)(Math.log(maxValue)/Math.log(2));
            int output = value;
            int bitCount = maxBitCount - 1;

            value >>= 1;
            while (value > 0)
            {
                output = (output << 1) | (value & 1);
                bitCount -= 1;
                value >>= 1;
            }

            return (output << bitCount) & ((1 << maxBitCount) - 1);
        }

        // Compute the FFT of an array of complex numbers
        public void fft(Complex[] x) {
            int n = x.length;

            // Base case
            if (n == 1) return;

            // Check if n is a power of 2
            if (Integer.bitCount(n) != 1) {
                throw new IllegalArgumentException("Length of x must be a power of 2");
            }

            // Divide
            Complex[] even = new Complex[n / 2];
            Complex[] odd = new Complex[n / 2];
            for (int i = 0; i < n / 2; i++) {
                even[i] = x[2 * i];
                odd[i] = x[2 * i + 1];
            }

            // Conquer
            fft(even);
            fft(odd);

            // Combine
            for (int k = 0; k < n / 2; k++) {
                double kth = -2 * k * Math.PI / n;
                Complex wk = new Complex(Math.cos(kth), Math.sin(kth));
                x[k] = even[k].plus(wk.times(odd[k]));
                x[k + n / 2] = even[k].minus(wk.times(odd[k]));
            }
        }

        // Complex number class
        public class Complex {
            private final double re;   // real part
            private final double im;   // imaginary part

            public Complex(double real, double imag) {
                re = real;
                im = imag;
            }

            public Complex plus(Complex b) {
                return new Complex(this.re + b.re, this.im + b.im);
            }

            public Complex minus(Complex b) {
                return new Complex(this.re - b.re, this.im - b.im);
            }

            public Complex times(Complex b) {
                return new Complex(this.re * b.re - this.im * b.im, this.re * b.im + this.im * b.re);
            }

            @Override
            public String toString() {
                if (im == 0) return re + "";
                return re + " " + im;
            }
        }
    }
}
