/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.microcks.testcontainers.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The daily statistic of a service mock invocations
 */
public class DailyInvocationStatistic {
    public static final String JSON_PROPERTY_ID = "id";
    private String id;

    public static final String JSON_PROPERTY_DAY = "day";
    private String day;

    public static final String JSON_PROPERTY_SERVICE_NAME = "serviceName";
    private String serviceName;

    public static final String JSON_PROPERTY_SERVICE_VERSION = "serviceVersion";
    private String serviceVersion;

    public static final String JSON_PROPERTY_DAILY_COUNT = "dailyCount";
    private BigDecimal dailyCount;

    public static final String JSON_PROPERTY_HOURLY_COUNT = "hourlyCount";
    private Map<String, Object> hourlyCount = new HashMap<>();

    public static final String JSON_PROPERTY_MINUTE_COUNT = "minuteCount";
    private Map<String, Object> minuteCount = new HashMap<>();

    public DailyInvocationStatistic() {
    }

    public DailyInvocationStatistic id(String id) {
        this.id = id;
        return this;
    }

    /**
     * Unique identifier of this statistic object
     * @return id
     */
    @JsonProperty(JSON_PROPERTY_ID)
    public String getId() {
        return id;
    }


    @JsonProperty(JSON_PROPERTY_ID)
    public void setId(String id) {
        this.id = id;
    }


    public DailyInvocationStatistic day(String day) {
        this.day = day;
        return this;
    }

    /**
     * The day (formatted as yyyyMMdd string) represented by this statistic
     * @return day
     */
    @JsonProperty(JSON_PROPERTY_DAY)
    public String getDay() {
        return day;
    }


    @JsonProperty(JSON_PROPERTY_DAY)
    public void setDay(String day) {
        this.day = day;
    }


    public DailyInvocationStatistic serviceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    /**
     * The name of the service this statistic is related to
     * @return serviceName
     */
    @JsonProperty(JSON_PROPERTY_SERVICE_NAME)
    public String getServiceName() {
        return serviceName;
    }


    @JsonProperty(JSON_PROPERTY_SERVICE_NAME)
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }


    public DailyInvocationStatistic serviceVersion(String serviceVersion) {
        this.serviceVersion = serviceVersion;
        return this;
    }

    /**
     * The version of the service this statistic is related to
     * @return serviceVersion
     */
    @JsonProperty(JSON_PROPERTY_SERVICE_VERSION)
    public String getServiceVersion() {
        return serviceVersion;
    }


    @JsonProperty(JSON_PROPERTY_SERVICE_VERSION)
    public void setServiceVersion(String serviceVersion) {
        this.serviceVersion = serviceVersion;
    }


    public DailyInvocationStatistic dailyCount(BigDecimal dailyCount) {
        this.dailyCount = dailyCount;
        return this;
    }

    /**
     * The number of service mock invocations on this day
     * @return dailyCount
     */
    @JsonProperty(JSON_PROPERTY_DAILY_COUNT)
    public BigDecimal getDailyCount() {
        return dailyCount;
    }


    @JsonProperty(JSON_PROPERTY_DAILY_COUNT)
    public void setDailyCount(BigDecimal dailyCount) {
        this.dailyCount = dailyCount;
    }


    public DailyInvocationStatistic hourlyCount(Map<String, Object> hourlyCount) {
        this.hourlyCount = hourlyCount;
        return this;
    }

    public DailyInvocationStatistic putHourlyCountItem(String key, Object hourlyCountItem) {
        if (this.hourlyCount == null) {
            this.hourlyCount = new HashMap<>();
        }
        this.hourlyCount.put(key, hourlyCountItem);
        return this;
    }

    /**
     * The number of service mock invocations per hour of the day (keys range from 0 to 23)
     * @return hourlyCount
     */
    @JsonProperty(JSON_PROPERTY_HOURLY_COUNT)
    public Map<String, Object> getHourlyCount() {
        return hourlyCount;
    }


    @JsonProperty(JSON_PROPERTY_HOURLY_COUNT)
    public void setHourlyCount(Map<String, Object> hourlyCount) {
        this.hourlyCount = hourlyCount;
    }


    public DailyInvocationStatistic minuteCount(Map<String, Object> minuteCount) {
        this.minuteCount = minuteCount;
        return this;
    }

    public DailyInvocationStatistic putMinuteCountItem(String key, Object minuteCountItem) {
        if (this.minuteCount == null) {
            this.minuteCount = new HashMap<>();
        }
        this.minuteCount.put(key, minuteCountItem);
        return this;
    }

    /**
     * The number of service mock invocations per minute of the day (keys range from 0 to 1439)
     * @return minuteCount
     */
    @JsonProperty(JSON_PROPERTY_MINUTE_COUNT)
    public Map<String, Object> getMinuteCount() {
        return minuteCount;
    }


    @JsonProperty(JSON_PROPERTY_MINUTE_COUNT)
    public void setMinuteCount(Map<String, Object> minuteCount) {
        this.minuteCount = minuteCount;
    }


    /**
     * Return true if this DailyInvocationStatistic object is equal to o.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DailyInvocationStatistic dailyInvocationStatistic = (DailyInvocationStatistic) o;
        return Objects.equals(this.id, dailyInvocationStatistic.id) &&
                Objects.equals(this.day, dailyInvocationStatistic.day) &&
                Objects.equals(this.serviceName, dailyInvocationStatistic.serviceName) &&
                Objects.equals(this.serviceVersion, dailyInvocationStatistic.serviceVersion) &&
                Objects.equals(this.dailyCount, dailyInvocationStatistic.dailyCount) &&
                Objects.equals(this.hourlyCount, dailyInvocationStatistic.hourlyCount) &&
                Objects.equals(this.minuteCount, dailyInvocationStatistic.minuteCount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, day, serviceName, serviceVersion, dailyCount, hourlyCount, minuteCount);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class DailyInvocationStatistic {\n");
        sb.append("    id: ").append(toIndentedString(id)).append("\n");
        sb.append("    day: ").append(toIndentedString(day)).append("\n");
        sb.append("    serviceName: ").append(toIndentedString(serviceName)).append("\n");
        sb.append("    serviceVersion: ").append(toIndentedString(serviceVersion)).append("\n");
        sb.append("    dailyCount: ").append(toIndentedString(dailyCount)).append("\n");
        sb.append("    hourlyCount: ").append(toIndentedString(hourlyCount)).append("\n");
        sb.append("    minuteCount: ").append(toIndentedString(minuteCount)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}


