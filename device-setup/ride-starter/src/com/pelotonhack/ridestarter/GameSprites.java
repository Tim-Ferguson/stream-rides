package com.pelotonhack.ridestarter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

final class GameSprites {
    private static final int[][] SOURCE_BOUNDS = {
            {20, 150, 590, 510},
            {585, 45, 1035, 510},
            {1015, 65, 1515, 520},
            {45, 525, 465, 935},
            {500, 525, 950, 970},
            {950, 570, 1515, 940}
    };

    private final Bitmap sheet;
    private final Rect source = new Rect();
    private final RectF destination = new RectF();

    GameSprites(Context context) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        sheet = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.game_sprites, options);
    }

    void draw(Canvas canvas, int index, float centerX, float centerY,
              float maxWidth, float maxHeight, Paint paint) {
        if (sheet == null || index < 0 || index >= SOURCE_BOUNDS.length) {
            return;
        }
        int[] bounds = SOURCE_BOUNDS[index];
        source.set(bounds[0], bounds[1], bounds[2], bounds[3]);

        float scale = Math.min(maxWidth / source.width(), maxHeight / source.height());
        float width = source.width() * scale;
        float height = source.height() * scale;
        destination.set(centerX - width / 2f, centerY - height / 2f,
                centerX + width / 2f, centerY + height / 2f);
        paint.setAlpha(255);
        canvas.drawBitmap(sheet, source, destination, paint);
    }
}
