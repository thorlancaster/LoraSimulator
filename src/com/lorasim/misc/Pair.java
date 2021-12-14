package com.lorasim.misc;

public class Pair<K, V> {
	private K k;
	private V v;
	public Pair(K k, V v){
		this.k = k;
		this.v = v;
	}
	public K getKey(){
		return k;
	}
	public V getValue(){
		return v;
	}
}
