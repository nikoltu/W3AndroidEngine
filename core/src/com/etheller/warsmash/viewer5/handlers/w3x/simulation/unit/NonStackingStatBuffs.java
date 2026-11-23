package com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.etheller.warsmash.util.Log;

/**
 * The non-stacking stat buffs on a unit, by stat and stacking key. Buffs under one key do not stack: the one with the
 * largest magnitude counts, except under {@link NonStackingStatBuff#ALLOW_STACKING_KEY}, where all of them add up.
 */
public final class NonStackingStatBuffs {
	private final Map<NonStackingStatBuffType, Map<String, List<NonStackingStatBuff>>> nonStackingBuffs = new HashMap<>();

	/**
	 * The live map of one stat, made on first use: attacks keep it, so it is never replaced once there. Units without
	 * buffs of a stat hold no map for it.
	 */
	public Map<String, List<NonStackingStatBuff>> get(final NonStackingStatBuffType type) {
		Map<String, List<NonStackingStatBuff>> buffKeyMap = this.nonStackingBuffs.get(type);
		if (buffKeyMap == null) {
			buffKeyMap = new HashMap<>(1);
			this.nonStackingBuffs.put(type, buffKeyMap);
		}
		return buffKeyMap;
	}

	public void add(final NonStackingStatBuff buff) {
		if (buff.getBuffType() == NonStackingStatBuffType.ALLATK) {
			add(NonStackingStatBuffType.MELEEATK, buff);
			add(NonStackingStatBuffType.RNGDATK, buff);
		} else if (buff.getBuffType() == NonStackingStatBuffType.ALLATKPCT) {
			add(NonStackingStatBuffType.MELEEATKPCT, buff);
			add(NonStackingStatBuffType.RNGDATKPCT, buff);
		} else {
			add(buff.getBuffType(), buff);
		}
	}

	private void add(final NonStackingStatBuffType type, final NonStackingStatBuff buff) {
		final Map<String, List<NonStackingStatBuff>> buffKeyMap = get(type);
		List<NonStackingStatBuff> theList = buffKeyMap.get(buff.getStackingKey());
		if (theList == null) {
			theList = new ArrayList<>();
			buffKeyMap.put(buff.getStackingKey(), theList);
		}
		theList.add(buff);
	}

	/**
	 * @param owner names the unit in the report of a buff that was not there
	 */
	public void remove(final NonStackingStatBuff buff, final Object owner) {
		if (buff.getBuffType() == NonStackingStatBuffType.ALLATK) {
			remove(NonStackingStatBuffType.MELEEATK, buff, owner);
			remove(NonStackingStatBuffType.RNGDATK, buff, owner);
		} else if (buff.getBuffType() == NonStackingStatBuffType.ALLATKPCT) {
			remove(NonStackingStatBuffType.MELEEATKPCT, buff, owner);
			remove(NonStackingStatBuffType.RNGDATKPCT, buff, owner);
		} else {
			remove(buff.getBuffType(), buff, owner);
		}
	}

	private void remove(final NonStackingStatBuffType type, final NonStackingStatBuff buff, final Object owner) {
		final Map<String, List<NonStackingStatBuff>> buffKeyMap = this.nonStackingBuffs.get(type);
		final List<NonStackingStatBuff> buffs = (buffKeyMap == null) ? null : buffKeyMap.get(buff.getStackingKey());
		if ((buffs == null) || !buffs.remove(buff)) {
			Log.error("Removing a " + type + " buff with key " + buff.getStackingKey() + " that " + owner
					+ " did not have");
			return;
		}
		if (buffs.isEmpty()) {
			buffKeyMap.remove(buff.getStackingKey());
		}
	}

	/** The running total plus the buffs of one stat, each stacking key's value once. */
	public float sum(final NonStackingStatBuffType type, float total) {
		final Map<String, List<NonStackingStatBuff>> buffKeyMap = this.nonStackingBuffs.get(type);
		if (buffKeyMap == null) {
			return total;
		}
		for (final Map.Entry<String, List<NonStackingStatBuff>> entry : buffKeyMap.entrySet()) {
			final Float buffForKey = valueForKey(entry.getKey(), entry.getValue());
			if (buffForKey == null) {
				continue;
			}
			total += buffForKey;
		}
		return total;
	}

	/** As {@link #sum(NonStackingStatBuffType, float)} for a percentage stat, each key's value times the base. */
	public float sum(final NonStackingStatBuffType type, float total, final float base) {
		final Map<String, List<NonStackingStatBuff>> buffKeyMap = this.nonStackingBuffs.get(type);
		if (buffKeyMap == null) {
			return total;
		}
		for (final Map.Entry<String, List<NonStackingStatBuff>> entry : buffKeyMap.entrySet()) {
			final Float buffForKey = valueForKey(entry.getKey(), entry.getValue());
			if (buffForKey == null) {
				continue;
			}
			total += buffForKey * base;
		}
		return total;
	}

	/** What the buffs under one stacking key give together, or null for none. */
	public static Float valueForKey(final String key, final List<NonStackingStatBuff> buffs) {
		Float buffForKey = null;
		for (final NonStackingStatBuff buff : buffs) {
			if (buffForKey == null) {
				buffForKey = buff.getValue();
			} else {
				if (key.equals(NonStackingStatBuff.ALLOW_STACKING_KEY)) {
					buffForKey += buff.getValue();
				} else {
					buffForKey = Math.abs(buffForKey) > Math.abs(buff.getValue()) ? buffForKey : buff.getValue();
				}
			}
		}
		return buffForKey;
	}
}
