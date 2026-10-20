package com.example.smart_workorder_system.viewmodel;

public class UiState<T> {
    public final boolean loading;
    public final T data;
    public final String error;
    public UiState(boolean loading,T data,String error) { this.loading=loading;this.data=data;this.error=error; }
    public static <T> UiState<T> idle() { return new UiState<>(false,null,null); }
}
