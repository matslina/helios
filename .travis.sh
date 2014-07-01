#!/bin/bash

case "$1" in
  before_install)
    sudo sh -c "wget -qO- https://get.docker.io/gpg | apt-key add -"
    sudo sh -c "echo deb http://get.docker.io/ubuntu docker main > /etc/apt/sources.list.d/docker.list"
    sudo apt-get update

    echo exit 101 | sudo tee /usr/sbin/policy-rc.d
    sudo chmod +x /usr/sbin/policy-rc.d
    sudo apt-get install -qy lxc-docker socat
    ;;

  before_script)
    export HOST_IP=`(/sbin/ifconfig eth0 || /sbin/ifconfig venet0:0) | grep 'inet addr' | awk -F: '{print $2}' | awk '{print $1}'`

    export DOCKER_HOST=tcp://$HOST_IP:2375
    export DOCKER_PORT_RANGE=2400:2500

    if [ "$TRAVIS_SECURE_ENV_VARS" == "true" ]
    then
      # this is a build from the spotify/helios repo, so we can use secure environment variables
      # and run against docker on our remote server

      # write the certificates and keys we need
      printf -- '-----BEGIN CERTIFICATE-----\nMIIDzTCCArWgAwIBAgIJAO7l9P+I2LpeMA0GCSqGSIb3DQEBCwUAMH0xCzAJBgNV\nBAYTAlVTMREwDwYDVQQIDAhOZXcgWW9yazERMA8GA1UEBwwIQnJvb2tseW4xEDAO\nBgNVBAoMB1Nwb3RpZnkxFDASBgNVBAMMC1JvaGFuIFNpbmdoMSAwHgYJKoZIhvcN\nAQkBFhFyb2hhbkBzcG90aWZ5LmNvbTAeFw0xNDA2MjkxOTA4MTFaFw0xNTA2Mjkx\nOTA4MTFaMH0xCzAJBgNVBAYTAlVTMREwDwYDVQQIDAhOZXcgWW9yazERMA8GA1UE\nBwwIQnJvb2tseW4xEDAOBgNVBAoMB1Nwb3RpZnkxFDASBgNVBAMMC1JvaGFuIFNp\nbmdoMSAwHgYJKoZIhvcNAQkBFhFyb2hhbkBzcG90aWZ5LmNvbTCCASIwDQYJKoZI\nhvcNAQEBBQADggEPADCCAQoCggEBAKag3GhNwFWuFQTQZK7s/08qEV1rILE+1tC/\n6ghcRqGlhU8kkMUloX6Tek4WPwoZboFkLpC/ZDUsBa5xePpYlNAr6iDVj3UYsdwm\n3+bkMCZAFUPJzpETplMUlNWWcVj0TmgkNWREnJpnJdJ59DsEOt373wm6SjZ7nnKl\nhjx9+n+vb5Un96ZtHLKq0mPofUVxGSHkB/92h7JB7XJJkvqtnCOwR4bRNYFdKuuW\nXjl4Vvb+HuOsBZnTt1XeEhGKmVwpo4uAlL5Pq2JHdcsZrb2prErpTCKWf27L51uy\nUcYjfqC/jIY7II44WbpNw9iXAhHH/DFIK/tu/mUdua12nmmntVUCAwEAAaNQME4w\nHQYDVR0OBBYEFBop+exgEutyqRdah6aIyROB3dc8MB8GA1UdIwQYMBaAFBop+exg\nEutyqRdah6aIyROB3dc8MAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQELBQADggEB\nAI6pCZOvv/KBhwcwqnotFEZb3Nu3bybQbaZtA7u+urvuZ1EtgGBHvdogBXNwTFgn\nbMPw0RFzrNlUH8hdNehkmdsaHmq9E7jTXIix7wdZK5lNWfRjxo03tBijLNAXwQq6\n6mXRNIGQNNKN5oJ/ElcXlkgSEZ7x/Nnr5cICyBWTH4QW78io3WzD/6ONEjZjnvsn\nI/u+57Dh8Jco5N2QHfCbsJfIim3DwPhht8Ol9OHC2y1PtnXxOsxG2834I1w8fNBS\nUjVT9XQRimKP9i8ZmLr3Di1wV9EhGFo0j6m+VcnMQlFeIXb+Rc04GGAmWemXGfHE\nKafHh8Zmfht63uHF/XOvjq4=\n-----END CERTIFICATE-----\n' \
          > /tmp/ca.pem
      printf -- '-----BEGIN CERTIFICATE-----\nMIIDjDCCAnSgAwIBAgIBAzANBgkqhkiG9w0BAQsFADB9MQswCQYDVQQGEwJVUzER\nMA8GA1UECAwITmV3IFlvcmsxETAPBgNVBAcMCEJyb29rbHluMRAwDgYDVQQKDAdT\ncG90aWZ5MRQwEgYDVQQDDAtSb2hhbiBTaW5naDEgMB4GCSqGSIb3DQEJARYRcm9o\nYW5Ac3BvdGlmeS5jb20wHhcNMTQwNjI5MTkxMDM5WhcNMTUwNjI5MTkxMDM5WjB9\nMQswCQYDVQQGEwJVUzERMA8GA1UECAwITmV3IFlvcmsxETAPBgNVBAcMCEJyb29r\nbHluMRAwDgYDVQQKDAdTcG90aWZ5MRQwEgYDVQQDDAtSb2hhbiBTaW5naDEgMB4G\nCSqGSIb3DQEJARYRcm9oYW5Ac3BvdGlmeS5jb20wggEiMA0GCSqGSIb3DQEBAQUA\nA4IBDwAwggEKAoIBAQDl3Bc8paaX5yMz/FTyB0xwdekkTqye/E4dBAhFMrUl3AnA\nVzlr7gUz5Fw2HBpEO99A5ehiMxIX0sb0kIRmJyHoc+9HusUYPSrlp4PNP7nWYuwB\n+tI+133J4rJbI9GzCQIR2xxTVwmKoOxyT7W/NVPrdRCp6bl5FWOWCq9afKkmWRTO\nNYHhnALtXyYtgU3qLfmA9qXfEK56NOkXYD+hZDFV/CsVsA0iE3HPv5DSFhPuLZyN\nIdtk8HvIhrFmrzoSatCALqDXG6E9hVIfyk4sApjsXI/TmgDBnYTHaNiL06GX3IBD\nPkgDUiSVvbxYdAbw1hr+v6WXPAV7Y5f5vMIAUeTXAgMBAAGjFzAVMBMGA1UdJQQM\nMAoGCCsGAQUFBwMCMA0GCSqGSIb3DQEBCwUAA4IBAQCaimAKavEFVfOuOFHLChOI\nB0gRx5GNhE+e+NYWGBkYTio0jO7Yn6cLTRxHtE4XU8GEvEgG+Dh8BVSHmS16w3Yx\n0lb33+/xA5tsqUmVnV/DyR0joTfGOl147Cz403ZsfMRpn/+0U+wor3OxfNSsYFuI\nSEaTjU8md/U8w7oCZVdx5/B0hnDdxTekvu2e1+wuhYzQr1CrR91ib+S52DPtb0qD\n642ZAco/0hrs85ltVo59zl1YfeuQTpIfZ8hj+7JYwyKZMuAN3OHI2SiDciKxgwdW\nghqWFOV9fhVRUiMA+pcEQPvPQK/SLuArRLXqgEK+GPceTGi6VceKEVkH+VgO2IRr\n-----END CERTIFICATE-----\n' \
          > /tmp/client-cert.pem
      printf -- '-----BEGIN RSA PRIVATE KEY-----\nProc-Type: 4,ENCRYPTED\nDEK-Info: DES-CBC,FF468B62C1CA91F6\n\nzfxyC2bl+WdHYs7SUd1HJzpjvJNJnwFAdqfcSi/S38lnO05FXlqGf2psfet/SaZL\n1ua5cNFiajbWlnp/6E1f917t/lUjjmtNEIMmHyKJJHnVPlZQu/GxuOcr2Ff+cqA0\nk6KGRssSeleDwtDRQrRfLN9R2JGsZT/TuPMpdzo5D9sfywMDZ3sBs1EQkRt4P/IM\nQQrEXgZdZe4ikUeZRm9FNFPr2MykodjhSLwOz9ymeAHUY/t/meYFLGtTQhG/fZTa\n5v/7K7AR2t+jbZm3f5O3HerzisqYwW3x+9/iJ7dCdYKO9HIfC7PbSoEKa9QS6YJ7\nz0kOg3GdKa+V/hNK9KW3CbSw6iwbiWLLJHRgmzRsWkMGV5yUHXtrxMg9koX4Ik71\n3ZQT7K10jcRKGhVAK34PE2+HY3pnhqT4Y5yS/Lzr4FSSzT9qf1YYn44wOaGzs/j/\nuC5/py/7VMn/9/q7NuQ9oxCsuA1k2qpF2OMCX7y1xKQCyo8OpMLaz269XeNqYpWO\nqgoM52cairHi3Ql00aTrZyQPPMfNnw1/P0IlBxLTZ1wsFrZrp5Xgb3OLdSp9n7CA\nd3q2iHenoQFfAuiOcNxN5Vm7YEZrQ9aYT5WNg2Zet+qsZFVo0I9Fw92izgXnqKJ7\nUd5M8orKtE9d4plbzyPGxUSMRUZYhLD/O11UcV7rOTHYBcQzHl+x4zWFh5SnaBUY\nUSjMPeVVrrIYCb99VxwbqZEgHuvNTdLhAoajXi6sZ8yEZu6ArHK2oZrL5PR+HkkH\n2aBPISc3xtaYvpa3OY5BcddVbTxRtikLFXj9RtH2uJEbpr4gTUNlphxXciro9HwX\no7SyPqNlLLb5eZIvF1vBwn2ScEMUEwXTBxoeVSQEBdToQQYenljj5F1ztlgoTsJg\n73sEnVul3N8k4iZqAkbKwQ++QdgLBzcVnkQFPg2OE80l8WxHPKLxeOkgEqY7H82H\n78OaMm5XTj4xHXGkkLdy371TMC14MLm+TDXIW8fQoU11vQh81rLBwIA/V7vq/b/e\nsxFBCp0l7G72u1l6gFhDtNyP1fRgheamIq0K2EFkFeqKA0qFuY6qxOECM//mZzNW\nZC3oSu//03nOPv0cigetJD2xQoyTinE5iNRMhM2BWttUEmpY+cYijlGhXm87G9Vy\nZiGG5Xz5y00lpBuZUYxpQ7QwJDkrLFdpGWzfh5IXW9EqnDFU/CJt8mAj0jEZFP4/\n0V0/eAYVaUaFe4UuyJywjF5i/bJRTGWkMpdYpOGM0gyZgyswOMKtXcNuJYKDKDWq\nZkwsvkXPZ+SQzMUzh/Fh2TBegZ3ZBavbahjn9GrjQXbvBnp1bv+kzp+D/o3piYit\nh+OnMWclcPakbFNtC9H+z2tbnHi8BiphyG7CtYmNj3I7LhqWm2XZK4rXCv3WdX2f\nX1tzCl3KQ8QS1q9WaMu/nG4rG/mq20pFu26mHfYJpBDMBg+fLlpaqygiRavbFCBC\nYjC7FmjE5cazpZnKAisNrANgPDzJ1eR3rj6ImHt9WZI88M6dVq15qbfV5eSZGBkU\nAN/rCaINW+WlyYV3jP/D3vYo++ajyDRQuwiYiAUxTYBJWOs8WuKj3g==\n-----END RSA PRIVATE KEY-----\n' \
          > /tmp/client-key.pem

      # decrypt the client key with the passphrase from secure environment
      openssl rsa -in /tmp/client-key.pem -passin env:PASSPHRASE -out /tmp/client-key.pem

      # proxy docker connections to our remote docker server 
      export SOCAT_DEFAULT_LISTEN_IP=$HOST_IP
      socat -d -d -v TCP-LISTEN:2375,fork OPENSSL:$REMOTE,cert=/tmp/client-cert.pem,cafile=/tmp/ca.pem,key=/tmp/client-key.pem &

      # also proxy the rest of the ports
      for port in $DOCKER_PORT_RANGE
      do
        socat -d TCP-LISTEN:$port,fork TCP:$REMOTE:$port &
      done
    else
      # this is a pull request from a fork, no secure environment variables or remote docker for
      # security reasons, so run docker in usermode linux

      git clone git://github.com/spotify/sekexe
      sudo apt-get install -qy slirp lxc

      export MAVEN_OPTS="-Xmx128m"

      export SLIRP_PORTS=`seq 2375 2500`

      sekexe/run "docker -d -H tcp://0.0.0.0:2375 " &
    fi

    while ! docker info; do sleep 1; done
    ;;
esac
