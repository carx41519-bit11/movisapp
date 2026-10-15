package com.movis.warehouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Three columns: a simple inventory mark that stays legible at small sizes. */
final class MovisMark extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    MovisMark(Context context,int color){super(context);paint.setColor(color);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/27f,getHeight()/32f);canvas.drawRoundRect(0,4,7,28,1.5f,1.5f,paint);canvas.drawRoundRect(10,11,17,28,1.5f,1.5f,paint);canvas.drawRoundRect(20,4,27,28,1.5f,1.5f,paint);canvas.restore();}
}
