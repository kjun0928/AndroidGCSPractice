package com.example.droneplz;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;

import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.Overlay;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.overlay.PolygonOverlay;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.ExperimentalApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.apis.solo.SoloCameraApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.android.client.utils.video.DecoderListener;
import com.o3dr.android.client.utils.video.MediaCodecManager;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.companion.solo.SoloAttributes;
import com.o3dr.services.android.lib.drone.companion.solo.SoloState;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.connection.ConnectionType;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.GuidedState;
import com.o3dr.services.android.lib.drone.property.Home;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.SimpleCommandListener;
import com.o3dr.services.android.lib.drone.mission.item.command.YawCondition;

import java.util.ArrayList;
import java.util.List;

import static com.o3dr.services.android.lib.drone.attribute.AttributeType.BATTERY;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, DroneListener, TowerListener, LinkListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private Drone drone;
    private int droneType = Type.TYPE_UNKNOWN;
    private ControlTower controlTower;
    private final Handler handler = new Handler();
    private LinearLayout armingbtn;
    private boolean connectDrone = false;
    private boolean maplock = false;
    private boolean mapfollow = true;


    private static final int DEFAULT_UDP_PORT = 14550;
    private static final int DEFAULT_USB_BAUD_RATE = 57600;

    private Spinner modeSelector;

    private Double takeoffAltitude = 0.0;

    Handler mainHandler;
    NaverMap myMap;
    LinearLayout linemenu;
    boolean menulist = false;
    LinearLayout lineAltimenu;
    boolean Altimenulist = false;
    ArrayList<Marker> listMarker = new ArrayList<>();
    int markerNumber;
    List<LatLng> coords = new ArrayList<>();
    PolygonOverlay polygon = new PolygonOverlay();
    Marker marker = new Marker();

    // 버튼들 선언
    private Button btnTakeoffAltitude;
    private Button btnTakeoffAltitudeUp;
    private Button btnTakeoffAltitudeDown;

    // 가이드모드 개체 선언
    GuideMode guideMode;

    private LatLong latLong;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);
        this.guideMode = new GuideMode(this);

        FragmentManager fm = getSupportFragmentManager();

        MapFragment mapFragment = (MapFragment) fm.findFragmentById(R.id.map);

        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.map, mapFragment).commit();
        }

        if (!connectDrone) {
            armingbtn = (LinearLayout) findViewById(R.id.connectmenu);
            armingbtn.setVisibility(View.INVISIBLE);
        }

        mapFragment.getMapAsync((OnMapReadyCallback) this);
        linemenu = findViewById(R.id.menu);
        linemenu.setVisibility(View.INVISIBLE);

        mapFragment.getMapAsync((OnMapReadyCallback) this);
        lineAltimenu = findViewById(R.id.Altimenu);
        lineAltimenu.setVisibility(View.INVISIBLE);

        this.modeSelector = (Spinner) findViewById(R.id.modeSelect);
        this.modeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onFlightModeSelected(view);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        btnTakeoffAltitude = findViewById(R.id.btnTakeoffAltitude);
        btnTakeoffAltitudeUp = findViewById(R.id.btnTakeoffAltitudeUp);
        btnTakeoffAltitudeDown = findViewById(R.id.btnTakeoffAltitudeDown);

        btnTakeoffAltitudeUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                upTakeoffAltitude();
            }
        });

        btnTakeoffAltitudeDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                downTakeoffAltitude();
            }
        });

    }

    @Override
    public void onStart() {
        super.onStart();
        this.controlTower.connect(this);
        updateVehicleModesForType(this.droneType);
    }


    @Override
    public void onStop() {
        super.onStop();
        if (this.drone.isConnected()) {
            this.drone.disconnect();
            updateConnectedButton(false);
        }

        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect();
    }

    public void BasicMap(View v) {
        myMap.setMapType(NaverMap.MapType.Basic);
    }

    public void NaviMap(View v) {
        myMap.setMapType(NaverMap.MapType.Navi);
    }

    public void SatelliteMap(View v) {
        myMap.setMapType(NaverMap.MapType.Satellite);
    }

    public void HybridMap(View v) {
        myMap.setMapType(NaverMap.MapType.Hybrid);
    }

    public void TerrainMap(View v) {
        myMap.setMapType(NaverMap.MapType.Terrain);
    }

    public void MapType(View v) {

        if (menulist) {
            menulist = !menulist;
            linemenu.setVisibility(View.INVISIBLE);
        } else {
            menulist = !menulist;
            linemenu.setVisibility(View.VISIBLE);
        }
    }

    protected void updateVehicleModesForType(int droneType) {

        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, android.R.layout.simple_spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.modeSelector.setAdapter(vehicleModeArrayAdapter);
    }

    protected void updateConnectedButton(Boolean isConnected) {
        Button connectButton = (Button) findViewById(R.id.btnConnect);
        if (isConnected) {
            connectButton.setText("Disconnect");
            armingbtn.setVisibility(View.INVISIBLE);
        } else {
            connectButton.setText("Connect");
            armingbtn.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        myMap = naverMap;

        myMap.setOnMapLongClickListener(new NaverMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(@NonNull PointF pointF, @NonNull LatLng latLng) {
                State vehicleState = drone.getAttribute(AttributeType.STATE);
                marker.setPosition(latLng);
                marker.setMap(myMap);
                latLong = new LatLong(latLng.latitude, latLng.longitude);
                guideMode.startGuideMode(drone,latLong);
/*
                if (vehicleState.isFlying()) {
                }
*/
            }
        });
    }

    // Drone Listener
    // ==========================================================

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
                checkSoloState();
                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone Disconnected");
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
                break;

            case AttributeEvent.STATE_UPDATED:
            case AttributeEvent.STATE_ARMING:
                updateArmButton();
                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType() != this.droneType) {
                    this.droneType = newDroneType.getDroneType();
                    updateVehicleModesForType(this.droneType);
                }
                break;


            case AttributeEvent.STATE_VEHICLE_MODE:
                updateVehicleMode();                                        //모드 업데이트
                break;

            case AttributeEvent.SPEED_UPDATED:
                updateSpeed();                                      //속도 업데이트
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                updateAltitude();                                       //고도 업데이트
                break;

            case AttributeEvent.BATTERY_UPDATED:
                updateBatteryVolt();                                        //전압 업데이트
                break;
/*
            case AttributeEvent.HOME_UPDATED:
                updateDistanceFromHome();                                       //거리 업데이트
                break;
*/
            case AttributeEvent.ATTITUDE_UPDATED:
                updateYaw();                                            //Yaw 업데이트
                break;

            case AttributeEvent.GPS_COUNT:
                updateNumberOfSatellites();                                         //위성 업데이트
                break;

            case AttributeEvent.GPS_POSITION:
                updateGPS();                                        //드론위치 업데이트
                break;

            default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }


    public void onBtnConnectTap(View view) {
        if (this.drone.isConnected()) {
            this.drone.disconnect();
        } else {
            Spinner connectionSelector = (Spinner) findViewById(R.id.selectConnectionType);
            int selectedConnectionType = connectionSelector.getSelectedItemPosition();

            ConnectionParameter connectionParams = selectedConnectionType == ConnectionType.TYPE_USB
                    ? ConnectionParameter.newUsbConnection(null)
                    : ConnectionParameter.newUdpConnection(null);

            this.drone.connect(connectionParams);
        }

    }

    protected void updateGPS() {
        Gps droneLocation = this.drone.getAttribute(AttributeType.GPS);
        marker.setPosition(new LatLng(droneLocation.getPosition().getLatitude(), droneLocation.getPosition().getLongitude()));
        marker.setMap(myMap);
        marker.setIcon(OverlayImage.fromResource(R.drawable.drone));
        marker.setAnchor(new PointF((float) 0.5, (float) 0.77));
        CameraUpdate cameraUpdate = CameraUpdate.scrollTo(new LatLng(droneLocation.getPosition().getLatitude(), droneLocation.getPosition().getLongitude()));
        myMap.moveCamera(cameraUpdate);

    }

    public void btn_event(View v) {
        switch (v.getId()) {
            case R.id.btnConnect:
                onBtnConnectTap(v);
                break;
            case R.id.btnarm:
                onArmButtonTap();
                break;
            case R.id.maplockbtn:
                maplock = !maplock;
                LinearLayout list = (LinearLayout) findViewById(R.id.maplocklayer);
                onMapbtnTap(list, maplock);
                break;
            case R.id.maplock:
                mapfollow = true;
                mapfollowTap();
                break;
            case R.id.mapmove:
                mapfollow = false;
                mapfollowTap();
                break;
        }

    }

    public void onFlightModeSelected(View view) {
        VehicleMode vehicleMode = (VehicleMode) this.modeSelector.getSelectedItem();

        VehicleApi.getApi(this.drone).setVehicleMode(vehicleMode, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Vehicle mode change successful.");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Vehicle mode change failed: " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Vehicle mode change timed out.");
            }
        });
    }

    public void mapfollowTap() {
        Button lockbtn = (Button) findViewById(R.id.maplockbtn);
        LinearLayout list = (LinearLayout) findViewById(R.id.maplocklayer);

        if (mapfollow)
            lockbtn.setText("맵 잠금");
        else
            lockbtn.setText("맵 이동");

        maplock = false;
        list.setVisibility(View.INVISIBLE);
    }

    protected void updateBatteryVolt() {
        TextView voltTextView = (TextView) findViewById(R.id.batteryVoltageValueTextView);
        Battery droneVolt = this.drone.getAttribute(BATTERY);
        Log.d("MYLOG", "베터리 변화 : " + droneVolt.getBatteryVoltage());
        voltTextView.setText("전압" + String.format(" " + droneVolt.getBatteryVoltage() + "V"));
    }

    protected void updateSpeed() {
        TextView speedTextView = (TextView) findViewById(R.id.speedValueTextView);
        Speed droneSpeed = this.drone.getAttribute(AttributeType.SPEED);
        speedTextView.setText("속도" + String.format("%3.1f", droneSpeed.getGroundSpeed()) + "m/s");
    }

    protected void updateYaw() {
        TextView yawTextView = (TextView) findViewById(R.id.yawValueTextView);
        Attitude droneyaw = this.drone.getAttribute(AttributeType.ATTITUDE);
        Log.d("MYLOG", "yaw : " + droneyaw.getYaw());
        yawTextView.setText("Yaw" + String.format("%3.1f", droneyaw.getYaw()) + "deg");
    }

    protected void updateAltitude() {
        TextView altitudeTextView = (TextView) findViewById(R.id.altitudeValueTextView);
        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        altitudeTextView.setText("고도" + String.format("%3.1f", droneAltitude.getAltitude()) + "m");
    }

    public void AltitudeSet(View v) {

        if (Altimenulist) {
            Altimenulist = !Altimenulist;
            lineAltimenu.setVisibility(View.INVISIBLE);
        } else {
            Altimenulist = !Altimenulist;
            lineAltimenu.setVisibility(View.VISIBLE);
        }
    }

    protected void targetAltitude() {

    }


    protected void updateNumberOfSatellites() {
        TextView numberOfSatellitesTextView = (TextView) findViewById(R.id.numberofSatellitesValueTextView);
        Gps droneNumberOfSatellites = this.drone.getAttribute(AttributeType.GPS);
        Log.d("MYLOG", "위성 수 변화 : " + droneNumberOfSatellites.getSatellitesCount());
        numberOfSatellitesTextView.setText("위성" + String.format("%d", droneNumberOfSatellites.getSatellitesCount()));
    }

    /*
        protected void updateDistanceFromHome() {
            TextView distanceTextView = (TextView) findViewById(R.id.distanceValueTextView);
            Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
            double vehicleAltitude = droneAltitude.getAltitude();
            Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
            LatLong vehiclePosition = droneGps.getPosition();

            double distanceFromHome = 0;

            if (droneGps.isValid()) {
                LatLongAlt vehicle3DPosition = new LatLongAlt(vehiclePosition.getLatitude(), vehiclePosition.getLongitude(), vehicleAltitude);
                Home droneHome = this.drone.getAttribute(AttributeType.HOME);
                distanceFromHome = distanceBetweenPoints(droneHome.getCoordinate(), vehicle3DPosition);
            } else {
                distanceFromHome = 0;
            }

            distanceTextView.setText("거리" + String.format("%3.1f", distanceFromHome) + "m");
        }
    */
    protected void alertUser(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, message);
    }

    protected double distanceBetweenPoints(LatLongAlt pointA, LatLongAlt pointB) {
        if (pointA == null || pointB == null) {
            return 0;
        }
        double dx = pointA.getLatitude() - pointB.getLatitude();
        double dy = pointA.getLongitude() - pointB.getLongitude();
        double dz = pointA.getAltitude() - pointB.getAltitude();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }


    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

    }

    private void runOnMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }

    @Override
    public void onLinkStateUpdated(@NonNull LinkConnectionStatus connectionStatus) {

    }

    private void checkSoloState() {
        final SoloState soloState = drone.getAttribute(SoloAttributes.SOLO_STATE);
        if (soloState == null) {
            alertUser("Unable to retrieve the solo state.");
        } else {
            alertUser("Solo state is up to date.");
        }
    }

    protected void updateVehicleMode() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.modeSelector.getAdapter();
        this.modeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }


    // DroneKit-Android Listener
    // ==========================================================

    @Override
    public void onTowerConnected() {
        alertUser("DroneKit-Android Connected");
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected() {
        alertUser("DroneKit-Android Interrupted");
    }

    protected void updateArmButton() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        Button armButton = (Button) findViewById(R.id.btnarm);

        if (!this.drone.isConnected()) {
            armingbtn.setVisibility(View.INVISIBLE);
        } else {
            armingbtn.setVisibility(View.VISIBLE);
        }

        if (vehicleState.isFlying()) {
            // Land
            armButton.setText("LAND");
        } else if (vehicleState.isArmed()) {
            // Take off
            armButton.setText("TAKE-OFF");
        } else if (vehicleState.isConnected()) {
            // Connected but not Armed
            armButton.setText("ARM");
        }
    }

    public void onArmButtonTap() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);

        if (vehicleState.isFlying()) {
            // Land
            VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener() {
                @Override
                public void onError(int executionError) {
                    alertUser("Unable to land the vehicle.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to land the vehicle.");
                }
            });
        } else if (vehicleState.isArmed()) {
            // Take off
            ControlApi.getApi(this.drone).takeoff(takeoffAltitude, new AbstractCommandListener() {

                @Override
                public void onSuccess() {
                    alertUser("Taking off...");
                }

                @Override
                public void onError(int i) {
                    alertUser("Unable to take off.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to take off.");
                }
            });
        } else if (!vehicleState.isConnected()) {
            // Connect
            alertUser("Connect to a drone first");
        } else {
            // Connected but not Armed
            VehicleApi.getApi(this.drone).arm(true, false, new SimpleCommandListener() {
                @Override
                public void onError(int executionError) {
                    alertUser("Unable to arm vehicle.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Arming operation timed out.");
                }
            });
        }
    }

    private void upTakeoffAltitude() {
        if (takeoffAltitude < 10.0) {
            takeoffAltitude += 0.5;
            btnTakeoffAltitude.setText("이륙고도" + takeoffAltitude + "m");
        }
    }

    private void downTakeoffAltitude() {
        if (takeoffAltitude > 1.5) {
            takeoffAltitude -= 0.5;
            btnTakeoffAltitude.setText("이륙고도" + takeoffAltitude + "m");
        }
    }

    public void onMapbtnTap(LinearLayout list, boolean visual) {
        if (visual) {
            list.setVisibility(View.VISIBLE);
        } else {
            list.setVisibility(View.INVISIBLE);
        }
    }

}