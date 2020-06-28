/*
Copyright (c) 2014 Hugo Vidal Teixeira

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package videochat;

import android.content.Intent;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import mobile.Application;


public class VideoChatImpl implements VideoChat {

	@Override public boolean open(short userId)
	{
		com.codename1.impl.android.CodenameOneActivity ctx = (com.codename1.impl.android.CodenameOneActivity) Application.getContext();
		android.content.Intent intent = new android.content.Intent(ctx, videochat.VideoChatActivity.class);
		ctx.startActivity(intent);
		return true;
	}

	@Override public boolean isSupported() {
		return true;
	}
}
