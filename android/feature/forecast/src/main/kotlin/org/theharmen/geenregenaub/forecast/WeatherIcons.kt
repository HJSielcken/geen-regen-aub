package org.theharmen.geenregenaub.forecast

import androidx.annotation.DrawableRes
import org.theharmen.geenregenaub.domain.WeatherCondition
import org.theharmen.geenregenaub.domain.WeatherCondition.CLEAR
import org.theharmen.geenregenaub.domain.WeatherCondition.DRIZZLE
import org.theharmen.geenregenaub.domain.WeatherCondition.FOG
import org.theharmen.geenregenaub.domain.WeatherCondition.FREEZING_DRIZZLE
import org.theharmen.geenregenaub.domain.WeatherCondition.FREEZING_RAIN
import org.theharmen.geenregenaub.domain.WeatherCondition.HEAVY_RAIN
import org.theharmen.geenregenaub.domain.WeatherCondition.HEAVY_RAIN_SHOWERS
import org.theharmen.geenregenaub.domain.WeatherCondition.HEAVY_SNOW
import org.theharmen.geenregenaub.domain.WeatherCondition.LIGHT_RAIN
import org.theharmen.geenregenaub.domain.WeatherCondition.LIGHT_SNOW
import org.theharmen.geenregenaub.domain.WeatherCondition.MAINLY_CLEAR
import org.theharmen.geenregenaub.domain.WeatherCondition.OVERCAST
import org.theharmen.geenregenaub.domain.WeatherCondition.PARTLY_CLOUDY
import org.theharmen.geenregenaub.domain.WeatherCondition.RAIN
import org.theharmen.geenregenaub.domain.WeatherCondition.RAIN_SHOWERS
import org.theharmen.geenregenaub.domain.WeatherCondition.SNOW
import org.theharmen.geenregenaub.domain.WeatherCondition.SNOW_GRAINS
import org.theharmen.geenregenaub.domain.WeatherCondition.SNOW_SHOWERS
import org.theharmen.geenregenaub.domain.WeatherCondition.THUNDERSTORM
import org.theharmen.geenregenaub.domain.WeatherCondition.THUNDERSTORM_HAIL
import org.theharmen.geenregenaub.domain.WeatherCondition.UNKNOWN

/**
 * Meteocons (fill style) by Bas Milius, MIT licence, https://github.com/basmilius/weather-icons.
 * Converted to vector drawables; the licence text is in res/raw/meteocons_license.txt.
 */
@DrawableRes
fun WeatherCondition.icon(isDay: Boolean = true): Int = when (this) {
    CLEAR -> if (isDay) R.drawable.wx_clear_day else R.drawable.wx_clear_night
    MAINLY_CLEAR, PARTLY_CLOUDY -> if (isDay) R.drawable.wx_partly_cloudy_day else R.drawable.wx_partly_cloudy_night
    OVERCAST -> R.drawable.wx_overcast
    FOG -> R.drawable.wx_fog
    DRIZZLE -> R.drawable.wx_drizzle
    LIGHT_RAIN, RAIN, HEAVY_RAIN, HEAVY_RAIN_SHOWERS -> R.drawable.wx_rain
    FREEZING_DRIZZLE, FREEZING_RAIN -> R.drawable.wx_sleet
    LIGHT_SNOW, SNOW, HEAVY_SNOW, SNOW_GRAINS -> R.drawable.wx_snow
    RAIN_SHOWERS -> if (isDay) R.drawable.wx_partly_cloudy_day_rain else R.drawable.wx_partly_cloudy_night_rain
    SNOW_SHOWERS -> if (isDay) R.drawable.wx_partly_cloudy_day_snow else R.drawable.wx_partly_cloudy_night_snow
    THUNDERSTORM -> if (isDay) R.drawable.wx_thunderstorms_day_rain else R.drawable.wx_thunderstorms_night_rain
    THUNDERSTORM_HAIL -> R.drawable.wx_thunderstorms_rain
    UNKNOWN -> R.drawable.wx_cloudy
}
