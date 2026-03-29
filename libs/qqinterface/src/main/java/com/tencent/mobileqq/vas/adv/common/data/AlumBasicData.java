package com.tencent.mobileqq.vas.adv.common.data;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import java.util.Map;

public class AlumBasicData implements Parcelable {
    public int adinfolength;
    public String advLogoUrl;
    public String advTextText;
    public String advTextTitle;
    public int advType;
    public String advimageUrl;
    public long aid;
    public int autoPlay;
    public String clickUrl;
    public Map<String, String> extendInfos;
    public int hasExposed;
    public int imageHeight;
    public int imageWith;
    public int innerAdShowType;
    public boolean isDtExpoReport;
    public boolean isFeedxLayer;
    public boolean isOriginalExposureReport;
    public String leftBottomText;
    public String negativeFeedbackUrl;
    public int preloadMiniApp;
    public String qqbexposureInfor;
    public String recCookie;
    public String rightBottomButton;
    public int sourceFrom;
    public String traceId;
    public String videoReportUrl;
    public int videoShowTime;
    public String videoUrl;

    protected AlumBasicData(Parcel in) {
    }

    public static final Creator<AlumBasicData> CREATOR = new Creator<>() {
        @Override
        public AlumBasicData createFromParcel(Parcel in) {
            return new AlumBasicData(in);
        }

        @Override
        public AlumBasicData[] newArray(int size) {
            return new AlumBasicData[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
    }
}
