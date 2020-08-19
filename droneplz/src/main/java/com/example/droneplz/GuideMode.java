package com.example.droneplz;

import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.property.GuidedState;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.model.AbstractCommandListener;


public class GuideMode {
    private LatLng mGuideModePoint; //가이드 목적지 저장
    private Marker mMarkerGuide = new com.naver.maps.map.overlay.Marker(); //GCS 위치표시 마커 옵션
    private OverlayImage guideIcon = OverlayImage.fromResource(R.drawable.marker_guide);
    private MainActivity mainActivity;

    public GuideMode(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    public void startGuideMode(final Drone drone, final LatLong point) {
        AlertDialog.Builder alt_bld = new AlertDialog.Builder(mainActivity);
        alt_bld.setMessage("확인하시면 가이드모드로 전환후 기체가 이동합니다.").setCancelable(false).setPositiveButton("확인", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // Action for 'Yes' Button
                VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_GUIDED, new AbstractCommandListener() {
                    @Override
                    public void onSuccess() {
                        ControlApi.getApi(drone).goTo(point, true, null);
                    }

                    @Override
                    public void onError(int i) {
                    }

                    @Override
                    public void onTimeout() {
                    }
                });
            }
        }).setNegativeButton("취소", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        AlertDialog alert = alt_bld.create();
        // Title for AlertDialog
        alert.setTitle("Title");
        // Icon for AlertDialog
        alert.setIcon(R.drawable.drone);
        alert.show();
    }

    public static boolean CheckGoal(final Drone drone, LatLng recentLatLng) {
        GuidedState guidedState = drone.getAttribute(AttributeType.GUIDED_STATE);
        LatLng target = new LatLng(guidedState.getCoordinate().getLatitude(), guidedState.getCoordinate().getLongitude());
        return target.distanceTo(recentLatLng) <= 1;
    }
}