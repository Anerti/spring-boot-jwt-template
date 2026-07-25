package com.techindna.springbootjwttemplate.mapper;

import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Continent;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Subdivision;
import com.techindna.springbootjwttemplate.entity.GeoIpResponse;
import org.springframework.stereotype.Component;

@Component
public class GeoIpMapper {

    public GeoIpResponse toGeoIpResponse(CityResponse response, String ip) {
        return new GeoIpResponse(
                ip,
                getName(response.getCity()),
                getName(response.getCountry()),
                getIsoCode(response.getCountry()),
                getName(response.getContinent()),
                getName(response.getMostSpecificSubdivision()),
                response.getPostal() != null ? response.getPostal().getCode() : null,
                response.getLocation() != null ? response.getLocation().getTimeZone() : null,
                response.getLocation() != null ? response.getLocation().getLatitude() : null,
                response.getLocation() != null ? response.getLocation().getLongitude() : null
        );
    }

    private String getName(City city) {
        return city != null ? city.getName() : null;
    }

    private String getName(Country country) {
        return country != null ? country.getName() : null;
    }

    private String getIsoCode(Country country) {
        return country != null ? country.getIsoCode() : null;
    }

    private String getName(Continent continent) {
        return continent != null ? continent.getName() : null;
    }

    private String getName(Subdivision subdivision) {
        return subdivision != null ? subdivision.getName() : null;
    }
}
