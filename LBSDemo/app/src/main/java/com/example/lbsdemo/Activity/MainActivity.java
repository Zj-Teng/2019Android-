package com.example.lbsdemo.Activity;

import androidx.appcompat.app.AppCompatActivity;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.Overlay;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.map.TextureMapView;
import com.baidu.mapapi.map.UiSettings;
import com.baidu.mapapi.model.LatLng;
import com.example.lbsdemo.Datebase.DBHelper;
import com.example.lbsdemo.Interface.UpdateUiCallBack;
import com.example.lbsdemo.Listeners.MyOrientationListener;
import com.example.lbsdemo.R;
import com.example.lbsdemo.Service.BindService;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private BindService bindService;
    private TextView textView;
    private Button startBtn;
    private TextureMapView mapView;
    private Button endBtn;
    private BaiduMap bmap = null;
    private SlidingMenu menu = null;
    private Button startStepBnt = null;
    private Button pauseStepBnt = null;
    private Button endStepBnt = null;
    private Button quitBnt = null;
    private LocationClient mLocationClient = null;
    private List<LatLng> points = null;
    private DBHelper dbHelper = null;
    private SQLiteDatabase db = null;
    private int steps = 0;
    private boolean isFirstLocate = true;
    private boolean isBind;
    private boolean isDraw = false;
    private enum stats {END,PAUSE,START}
    private stats stat = stats.START;
    private MyOrientationListener myOrientationListener = null;
    private float myCurrentX = 0f;
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(@org.jetbrains.annotations.NotNull Message msg) {
            if (msg.what == 1) {
                textView.setText("步数为：" + msg.arg1 + "");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SDKInitializer.initialize(getApplicationContext());
        setContentView(R.layout.activity_main);
        initLeftMenu();
        registerViews();
        initMapLocation();
        initDatabase();
        startStepor();
        registerEvents();
    }

    //和绷定服务数据交换的桥梁，可以通过IBinder service获取服务的实例来调用服务的方法或者数据
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BindService.LcBinder lcBinder = (BindService.LcBinder) service;
            bindService = lcBinder.getService();
            bindService.registerCallback(new UpdateUiCallBack() {
                @Override
                public void updateUi(int stepCount) {
                    //当前接收到stepCount数据，就是最新的步数
                    Message message = Message.obtain();
                    switch (stat){
                        case START:
                            message.what = 1;
                            message.arg1 = stepCount;
                            steps = stepCount;
                            handler.sendMessage(message);
                            Log.i("MainActivity—updateUi", "当前步数" + stepCount);
                            break;
                        case PAUSE:
                            message.what = 1;
                            message.arg1 = steps;
                            handler.sendMessage(message);
                            Log.i("MainActivity—updateUi", "当前步数" + steps);
                            break;
                        case END:
                            Log.i("MainActivity—updateUi", "停止计步");
                            break;
                    }
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    //对位置服务的监听
    public class MyLocationListener extends BDAbstractLocationListener {
        @Override
        public void onReceiveLocation(BDLocation location) {
            // map view 销毁后不在处理新接收的位置
            if (location == null || mapView == null)
                return;
            int errorCode = location.getLocType();
            Log.d("TAG", Integer.toString(errorCode));
            MyLocationData locData = new MyLocationData.Builder()
                    .accuracy(location.getRadius())
                    // 此处设置开发者获取到的方向信息，顺时针0-360
                    .direction(myCurrentX)
                    .latitude(location.getLatitude())
                    .longitude(location.getLongitude()).build();
            bmap.setMyLocationData(locData);
            double lat = location.getLatitude();
            double lon = location.getLongitude();
            if (isFirstLocate) {
                isFirstLocate = false;
                LatLng ll = new LatLng(location.getLatitude(),
                        location.getLongitude());
                // MapStatusUpdate u = MapStatusUpdateFactory.newLatLng(ll);
                // 设置缩放比例,更新地图状态
                float f = bmap.getMaxZoomLevel();// 19.0
                MapStatusUpdate u = MapStatusUpdateFactory.newLatLngZoom(ll,
                        f - 2);
                bmap.animateMapStatus(u);
                //地图位置显示
                Toast.makeText(MainActivity.this, location.getAddrStr(),
                        Toast.LENGTH_SHORT).show();
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy年MM月dd日");
                Date date = new Date(System.currentTimeMillis());
                String currentData = simpleDateFormat.format(date);
                Cursor cursor = db.rawQuery("Select * from STEPS where date = ?", new String[]{currentData});
                ContentValues values = new ContentValues();
                values.put("date", currentData);
                values.put("latitude", lat);
                values.put("lontitude", lon);
                db.insert("POSITION", null, values);
                values.clear();
                if (cursor.getCount() == 0){
                    values.put("date", currentData);
                    values.put("step", steps);
                    db.insert("STEPS", null, values);
                }else {
                    values.put("step", steps);
                    db.update("STEPS", values, "date = ?", new String[]{currentData});
                }
                points = new ArrayList<LatLng>();
                LatLng point = new LatLng(lat, lon);
                points.add(point);
            }else {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy年MM月dd日");
                Date date = new Date(System.currentTimeMillis());
                String currentData = simpleDateFormat.format(date);
                ContentValues values = new ContentValues();
                values.put("date", currentData);
                values.put("latitude", lat);
                values.put("lontitude", lon);
                db.insert("POSITION", null, values);
                values.clear();
                values.put("step", steps);
                db.update("STEPS", values, "date = ?", new String[]{currentData});
            }
            if (isDraw){
                LatLng point = new LatLng(lat, lon);
                points.add(point);
                //设置折线的属性
                OverlayOptions mOverlayOptions = new PolylineOptions()
                        .width(10)
                        .color(0xAAFF0000)
                        .points(points);
                //在地图上绘制折线
                //mPloyline 折线对象
                Overlay mPolyline = bmap.addOverlay(mOverlayOptions);
            }else {
                points.clear();
                LatLng point = new LatLng(lat, lon);
                points.add(point);
            }
        }
    }

    //对使用方向传感器的监听
    private void useLocationOrientationListener() {
        myOrientationListener = new MyOrientationListener(MainActivity.this);
        myOrientationListener.setMyOrientationListener(new MyOrientationListener.onOrientationListener() {
            @Override
            public void onOrientationChanged(float x) {//监听方向的改变，方向改变时，需要得到地图上方向图标的位置
                myCurrentX=x;
                System.out.println("方向:x---->"+x);
            }
        });
    }

    //初始化地图定位
    private void initMapLocation(){
        mLocationClient = new LocationClient(MainActivity.this);
        mLocationClient.registerLocationListener(new MyLocationListener());
        LocationClientOption option = new LocationClientOption();
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
//        option.setOpenGps(true);// 打开gps
        option.setIsNeedAddress(true);//返回当前的位置信息，如果不设置为true，默认就为false，就无法获得位置的信息
        option.setCoorType("bd09ll"); // 设置坐标类型
        option.setScanSpan(1000);// 设置发起定位请求的间隔时间为1000ms
        option.setIsNeedAddress(true);
        mLocationClient.setLocOption(option);
        mLocationClient.start();
        useLocationOrientationListener();
        myOrientationListener.start();
    }

    //初始化地图
    private void initMap(){
        bmap = mapView.getMap();
        bmap.setMyLocationEnabled(true);
        MyLocationConfiguration config = new MyLocationConfiguration(MyLocationConfiguration.LocationMode.COMPASS, true, null);
        bmap.setMyLocationConfigeration(config);
        UiSettings settings = bmap.getUiSettings();
        settings.setScrollGesturesEnabled(false);
    }

    //初始化数据库
    private void initDatabase(){
        dbHelper = new DBHelper(this, "Position.db", null, 1);
        db = dbHelper.getWritableDatabase();
    }

    //初始化左边侧滑菜单
    private void initLeftMenu(){
        menu = new SlidingMenu(MainActivity.this);
        menu.setMode(SlidingMenu.LEFT);
        menu.setBehindWidth(500);
        menu.setFadeDegree(0.35f);
        menu.setMenu(R.layout.left_menu);
        menu.setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);
        menu.setTouchModeBehind(SlidingMenu.TOUCHMODE_FULLSCREEN);
        menu.showContent();
        menu.attachToActivity(MainActivity.this, SlidingMenu.SLIDING_CONTENT);
    }

    //开启计步服务
    private void startStepor(){
        //绷定并且开启一个服务，绷定是为了方便数据交换，启动是为了当当前app不在活动页的时候，
        // 计步服务不会被关闭。需要保证当activity不为活跃状态是计步服务在后台能一直运行！
        Intent intent = new Intent(MainActivity.this, BindService.class);
        isBind = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        startService(intent);
    }

    //对组件的注册
    private void registerViews(){
        mapView = (TextureMapView) findViewById(R.id.map);
        initMap();
        View view = menu.getMenu();
        textView = (TextView) view.findViewById(R.id.text);
        startBtn = (Button) findViewById(R.id.start);
        endBtn = (Button) findViewById(R.id.end);
        startStepBnt = (Button) view.findViewById(R.id.startStepor);
        pauseStepBnt = (Button) view.findViewById(R.id.pauseStepor);
        endStepBnt = (Button) view.findViewById(R.id.endStepor);
        quitBnt = (Button) view.findViewById(R.id.quit);
        endBtn.setVisibility(View.INVISIBLE);
    }

    //对组件事件的注册
    private void registerEvents(){
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "开始绘制轨迹", Toast.LENGTH_LONG).show();
                bmap.clear();
                isDraw = true;
                startBtn.setVisibility(View.INVISIBLE);
                endBtn.setVisibility(View.VISIBLE);
                mLocationClient.start();
            }
        });
        endBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "停止绘制轨迹", Toast.LENGTH_LONG).show();
                isDraw = false;
                startBtn.setVisibility(View.VISIBLE);
                endBtn.setVisibility(View.INVISIBLE);
            }
        });
        startStepBnt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "开始计步", Toast.LENGTH_SHORT).show();
                stat = stats.START;
                if (!isBind){
                    startStepor();
                }
                isBind = true;
            }
        });
        pauseStepBnt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "暂停计步", Toast.LENGTH_SHORT).show();
                stat = stats.PAUSE;
            }
        });
        endStepBnt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "停止计步", Toast.LENGTH_SHORT).show();
                stat = stats.END;
                if (isBind){
                    bindService.stopSelf();
                }
                isBind = false;
            }
        });
        quitBnt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDestroy();
            }
        });
    }

    @Override
    protected void onResume() {
        mapView.onResume();
        super.onResume();
    }

    @Override
    protected void onPause() {
        mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mLocationClient.stop();
        bmap.setMyLocationEnabled(false);
        mapView.onDestroy();
        mapView = null;
        db.close();
        if (isBind){
            this.unbindService(serviceConnection);
        }
        super.onDestroy();
    }
}