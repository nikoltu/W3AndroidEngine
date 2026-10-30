package com.etheller.warsmash.viewer5.handlers.mdx;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.math.Matrix4;

public abstract class GenericGroup {
	public final List<Integer> objects;
	private int[] objectArray = new int[0];

	public abstract void render(MdxComplexInstance instance, Matrix4 mvp);

	public GenericGroup() {
		this.objects = new ArrayList<>(); // TODO IntArrayList
	}

	// Groups are rendered for every instance each frame; iterating the boxed list allocated an iterator each time.
	protected int[] getObjectArray() {
		if (this.objectArray.length != this.objects.size()) {
			final int[] array = new int[this.objects.size()];
			for (int i = 0; i < array.length; i++) {
				array[i] = this.objects.get(i);
			}
			this.objectArray = array;
		}
		return this.objectArray;
	}

}
