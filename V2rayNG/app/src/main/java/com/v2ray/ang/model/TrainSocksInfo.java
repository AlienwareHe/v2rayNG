package com.v2ray.ang.model;

public class TrainSocksInfo {
    private String exportIp;
    private String id;
    private String proxyGroup;
    private String proxyIp;
    private int proxyPort;
    private boolean state;

    public String getExportIp() {
        return exportIp;
    }

    public void setExportIp(String exportIp) {
        this.exportIp = exportIp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProxyGroup() {
        return proxyGroup;
    }

    public void setProxyGroup(String proxyGroup) {
        this.proxyGroup = proxyGroup;
    }

    public String getProxyIp() {
        return proxyIp;
    }

    public void setProxyIp(String proxyIp) {
        this.proxyIp = proxyIp;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public boolean isState() {
        return state;
    }

    public void setState(boolean state) {
        this.state = state;
    }
}
