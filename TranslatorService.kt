package com.example.screentranslator

import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import android.widget.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TranslatorService : Service() {
    companion object { const val START="START"; const val CODE="CODE"; const val DATA="DATA" }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var wm: WindowManager? = null
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var selection: View? = null
    private var translating = false
    private var left=0; private var top=0; private var right=0; private var bottom=0
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val http=OkHttpClient.Builder().callTimeout(12,TimeUnit.SECONDS).build()

    override fun onStartCommand(i:Intent?,flags:Int,id:Int):Int {
        if(i?.action==START){ notification(); startProjection(i) }
        return START_NOT_STICKY
    }
    private fun notification(){
        val ch=NotificationChannel("translator","Screen Translator",NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        startForeground(1,Notification.Builder(this,"translator")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("Screen Translator")
            .setContentText("Выберите область текста")
            .build())
    }
    private fun startProjection(i:Intent){
        val data=i.getParcelableExtra<Intent>(DATA) ?: return
        val pm=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection=pm.getMediaProjection(i.getIntExtra(CODE,-1),data)
        val dm=resources.displayMetrics
        reader=ImageReader.newInstance(dm.widthPixels,dm.heightPixels,PixelFormat.RGBA_8888,2)
        reader!!.setOnImageAvailableListener({r->
            if(!translating){r.acquireLatestImage()?.close();return@setOnImageAvailableListener}
            translating=false
            r.acquireLatestImage()?.let{process(it)}
        },Handler(Looper.getMainLooper()))
        display=projection!!.createVirtualDisplay("ScreenTranslator",dm.widthPixels,dm.heightPixels,dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,null)
        showControls()
    }

    private fun showControls(){
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        bubble=button("ВЫБРАТЬ") { showSelector() }
        val bp=WindowManager.LayoutParams(220,85,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT)
        bp.gravity=Gravity.TOP or Gravity.END; bp.y=140
        wm!!.addView(bubble,bp)
    }

    private fun showSelector(){
        selection=SelectionView(this){l,t,r,b->
            left=l;top=t;right=r;bottom=b
            wm?.removeView(selection);selection=null
            bubble?.text="ПЕРЕВЕСТИ"
            bubble?.setOnClickListener{translating=true; bubble?.text="СКАН…"}
        }
        val lp=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT)
        wm?.addView(selection,lp)
    }

    private fun process(image:Image){
        try{
            val bmp=imageToBitmap(image) ?: return
            val l=left.coerceIn(0,bmp.width); val t=top.coerceIn(0,bmp.height)
            val rr=right.coerceIn(l+1,bmp.width); val bb=bottom.coerceIn(t+1,bmp.height)
            val crop=Bitmap.createBitmap(bmp,l,t,rr-l,bb-t); bmp.recycle()
            recognizer.process(InputImage.fromBitmap(crop,0))
                .addOnSuccessListener{res->
                    val txt=res.text.trim()
                    if(txt.isNotEmpty()) translate(txt) else status("Текст не найден")
                }.addOnFailureListener{status("OCR ошибка")}
                .addOnCompleteListener{crop.recycle()}
        }finally{image.close()}
    }

    private fun imageToBitmap(image:Image):Bitmap?{
        val p=image.planes.firstOrNull() ?: return null
        val bmp=Bitmap.createBitmap(image.width,image.height,Bitmap.Config.ARGB_8888)
        val src=p.buffer; val stride=p.rowStride; val pix=p.pixelStride
        val row=image.width*pix; val tmp=ByteArray(row); val out=ByteArray(row*image.height)
        for(y in 0 until image.height){src.position(y*stride);src.get(tmp,0,row);System.arraycopy(tmp,0,out,y*row,row)}
        bmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(out));return bmp
    }

    private fun translate(text:String){
        status("Перевожу…")
        val base=getSharedPreferences("settings",MODE_PRIVATE)
            .getString("api","https://libretranslate.com")!!.trimEnd('/')
        scope.launch{
            try{
                val body=JSONObject().put("q",text).put("source","en").put("target","ru").put("format","text")
                    .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val req=Request.Builder().url("$base/translate").post(body).build()
                http.newCall(req).execute().use{res->
                    val raw=res.body?.string().orEmpty()
                    if(!res.isSuccessful)throw Exception("HTTP ${res.code}")
                    val ru=JSONObject(raw).optString("translatedText")
                    withContext(Dispatchers.Main){showResult(ru)}
                }
            }catch(e:Exception){withContext(Dispatchers.Main){status("Ошибка перевода")}}
        }
    }

    private fun showResult(text:String){
        panel?.let{wm?.removeView(it)}
        panel=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(24,18,24,18)
            background=GradientDrawable().apply{setColor(0xE6000000.toInt());cornerRadius=18f}
            addView(TextView(this@TranslatorService).apply{
                this.text=text;setTextColor(Color.WHITE);textSize=18f
            })
            addView(Button(this@TranslatorService).apply{
                this.text="Закрыть"
                setOnClickListener{panel?.visibility=View.GONE}
            })
        }
        val lp=WindowManager.LayoutParams(-1,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT)
        lp.gravity=Gravity.BOTTOM;lp.y=90
        wm?.addView(panel,lp)
        bubble?.text="ВЫБРАТЬ"
        bubble?.setOnClickListener{showSelector()}
    }

    private fun button(text:String,click:()->Unit)=TextView(this).apply{
        this.text=text;textSize=14f;setTextColor(Color.WHITE);setPadding(20,16,20,16)
        background=GradientDrawable().apply{setColor(0xDD222222.toInt());cornerRadius=20f}
        setOnClickListener{click()}
    }
    private fun status(s:String){Handler(Looper.getMainLooper()).post{bubble?.text=s}}
    override fun onDestroy(){
        scope.cancel();display?.release();reader?.close();projection?.stop();recognizer.close()
        bubble?.let{wm?.removeView(it)};panel?.let{wm?.removeView(it)};selection?.let{wm?.removeView(it)}
        super.onDestroy()
    }
    override fun onBind(i:Intent?)=null
}

class SelectionView(context:Context,private val done:(Int,Int,Int,Int)->Unit):View(context){
    private var sx=0f;private var sy=0f;private var ex=0f;private var ey=0f;private var active=false
    private val p=Paint().apply{style=Paint.Style.STROKE;strokeWidth=5f;color=Color.WHITE}
    override fun onDraw(c:Canvas){super.onDraw(c);c.drawColor(0x55000000);if(active)c.drawRect(sx,sy,ex,ey,p)}
    override fun onTouchEvent(e:android.view.MotionEvent):Boolean{
        when(e.action){
            MotionEvent.ACTION_DOWN->{sx=e.x;sy=e.y;ex=sx;ey=sy;active=true;invalidate();return true}
            MotionEvent.ACTION_MOVE->{ex=e.x;ey=e.y;invalidate();return true}
            MotionEvent.ACTION_UP->{
                ex=e.x;ey=e.y;active=false;invalidate()
                val l=minOf(sx,ex).toInt();val t=minOf(sy,ey).toInt()
                val r=maxOf(sx,ex).toInt();val b=maxOf(sy,ey).toInt()
                if(r-l>20&&b-t>20)done(l,t,r,b)
                return true
            }
        };return true
    }
}
