package com.rewardshopplanner.data;

import java.util.List;
import lombok.Getter;

/**
 * A place where an activity's on-screen progress shows: whole map regions, or a polygon of tiles
 * walked in game (corners in order). It can be limited to some currencies, e.g. the Mining Guild
 * shows only unidentified minerals.
 */
@Getter
public class Area
{
	/** Plane the area is on; null for any. */
	private Integer plane;
	private int[] regions;
	/** Polygon corners as [x, y] tiles; the tiles on its edges are inside. */
	private int[][] points;
	/** Currencies shown here; null or empty for all of the activity's. */
	private List<String> currencies;

	public boolean contains(int regionId, int x, int y, int tilePlane)
	{
		if (plane != null && plane != tilePlane)
		{
			return false;
		}
		if (regions != null)
		{
			for (int region : regions)
			{
				if (region == regionId)
				{
					return true;
				}
			}
		}
		return points != null && points.length >= 3 && inPolygon(x, y);
	}

	private boolean inPolygon(int x, int y)
	{
		boolean inside = false;
		for (int i = 0, j = points.length - 1; i < points.length; j = i++)
		{
			int x1 = points[i][0];
			int y1 = points[i][1];
			int x2 = points[j][0];
			int y2 = points[j][1];
			if (onSegment(x, y, x1, y1, x2, y2))
			{
				return true;
			}
			// ray casting to the east
			if ((y1 > y) != (y2 > y) && x < (double) (x2 - x1) * (y - y1) / (y2 - y1) + x1)
			{
				inside = !inside;
			}
		}
		return inside;
	}

	/** Whether the tile lies on the edge between two corners (within half a tile). */
	private static boolean onSegment(int x, int y, int x1, int y1, int x2, int y2)
	{
		double dx = x2 - x1;
		double dy = y2 - y1;
		double length = dx * dx + dy * dy;
		double t = length == 0 ? 0 : Math.max(0, Math.min(1, ((x - x1) * dx + (y - y1) * dy) / length));
		double px = x1 + t * dx - x;
		double py = y1 + t * dy - y;
		return px * px + py * py <= 0.25;
	}
}
