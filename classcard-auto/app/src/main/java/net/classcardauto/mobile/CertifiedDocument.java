package net.classcardauto.mobile;

import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: CertificateVerifier.kt */
/* loaded from: classes.dex */
public final /* data */ class CertifiedDocument {
    private final String computerId;
    private final String computerModel;
    private final String computerName;
    private final String deviceId;
    private final String issuedAt;
    private final String number;
    private final String packageName;

    public CertifiedDocument(String issuedAt, String number, String computerName, String computerModel, String computerId, String deviceId, String packageName) {
        Intrinsics.checkNotNullParameter(issuedAt, "issuedAt");
        Intrinsics.checkNotNullParameter(number, "number");
        Intrinsics.checkNotNullParameter(computerName, "computerName");
        Intrinsics.checkNotNullParameter(computerModel, "computerModel");
        Intrinsics.checkNotNullParameter(computerId, "computerId");
        Intrinsics.checkNotNullParameter(deviceId, "deviceId");
        Intrinsics.checkNotNullParameter(packageName, "packageName");
        this.issuedAt = issuedAt;
        this.number = number;
        this.computerName = computerName;
        this.computerModel = computerModel;
        this.computerId = computerId;
        this.deviceId = deviceId;
        this.packageName = packageName;
    }

    public static /* synthetic */ CertifiedDocument copy$default(CertifiedDocument certifiedDocument, String str, String str2, String str3, String str4, String str5, String str6, String str7, int i, Object obj) {
        if ((i & 1) != 0) {
            str = certifiedDocument.issuedAt;
        }
        if ((i & 2) != 0) {
            str2 = certifiedDocument.number;
        }
        if ((i & 4) != 0) {
            str3 = certifiedDocument.computerName;
        }
        if ((i & 8) != 0) {
            str4 = certifiedDocument.computerModel;
        }
        if ((i & 16) != 0) {
            str5 = certifiedDocument.computerId;
        }
        if ((i & 32) != 0) {
            str6 = certifiedDocument.deviceId;
        }
        if ((i & 64) != 0) {
            str7 = certifiedDocument.packageName;
        }
        String str8 = str6;
        String str9 = str7;
        String str10 = str5;
        String str11 = str3;
        return certifiedDocument.copy(str, str2, str11, str4, str10, str8, str9);
    }








    public final CertifiedDocument copy(String issuedAt, String number, String computerName, String computerModel, String computerId, String deviceId, String packageName) {
        Intrinsics.checkNotNullParameter(issuedAt, "issuedAt");
        Intrinsics.checkNotNullParameter(number, "number");
        Intrinsics.checkNotNullParameter(computerName, "computerName");
        Intrinsics.checkNotNullParameter(computerModel, "computerModel");
        Intrinsics.checkNotNullParameter(computerId, "computerId");
        Intrinsics.checkNotNullParameter(deviceId, "deviceId");
        Intrinsics.checkNotNullParameter(packageName, "packageName");
        return new CertifiedDocument(issuedAt, number, computerName, computerModel, computerId, deviceId, packageName);
    }

    public final String display() {
        String str = this.issuedAt;
        String str2 = this.number;
        String str3 = this.computerName;
        String str4 = this.computerModel;
        return "인증 시각 – 인증 번호 – 인증에 사용된 컴퓨터기기\n\n" + str + " – " + str2 + " – " + str3 + " (" + str4 + ")\n\n인증 시각: " + str + "\n인증 번호: " + str2 + "\n인증 컴퓨터: " + str3 + "\n컴퓨터 기종: " + str4 + "\n컴퓨터 식별 번호: " + this.computerId + "\n휴대폰 식별 번호: " + this.deviceId + "\n\n이 컴퓨터가 발급한 전자서명과 현재 휴대폰 연결 정보를 확인한 인증서입니다.";
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CertifiedDocument)) {
            return false;
        }
        CertifiedDocument certifiedDocument = (CertifiedDocument) other;
        return Intrinsics.areEqual(this.issuedAt, certifiedDocument.issuedAt) && Intrinsics.areEqual(this.number, certifiedDocument.number) && Intrinsics.areEqual(this.computerName, certifiedDocument.computerName) && Intrinsics.areEqual(this.computerModel, certifiedDocument.computerModel) && Intrinsics.areEqual(this.computerId, certifiedDocument.computerId) && Intrinsics.areEqual(this.deviceId, certifiedDocument.deviceId) && Intrinsics.areEqual(this.packageName, certifiedDocument.packageName);
    }

    public final String getComputerId() {
        return this.computerId;
    }

    public final String getComputerModel() {
        return this.computerModel;
    }

    public final String getComputerName() {
        return this.computerName;
    }

    public final String getDeviceId() {
        return this.deviceId;
    }

    public final String getIssuedAt() {
        return this.issuedAt;
    }

    public final String getNumber() {
        return this.number;
    }

    public final String getPackageName() {
        return this.packageName;
    }

    public int hashCode() {
        return (((((((((((this.issuedAt.hashCode() * 31) + this.number.hashCode()) * 31) + this.computerName.hashCode()) * 31) + this.computerModel.hashCode()) * 31) + this.computerId.hashCode()) * 31) + this.deviceId.hashCode()) * 31) + this.packageName.hashCode();
    }

    public String toString() {
        return "CertifiedDocument(issuedAt=" + this.issuedAt + ", number=" + this.number + ", computerName=" + this.computerName + ", computerModel=" + this.computerModel + ", computerId=" + this.computerId + ", deviceId=" + this.deviceId + ", packageName=" + this.packageName + ")";
    }
}
