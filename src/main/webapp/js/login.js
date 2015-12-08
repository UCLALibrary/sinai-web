// OAuth login using HelloJS

function login(site) {
  hello(site).login({ 'scope' : 'email' }).then(
    function() {
      var session = hello(site).getAuthResponse();
      var xhr = new XMLHttpRequest();

      xhr.open('POST', '/');
      xhr.setRequestHeader('Content-type', 'application/x-www-form-urlencoded');

      xhr.onload = function() {
        if (this.responseText == 'success') {

          // Let's give more of a visual representation of being logged in
          if (document.getElementById(site + '-login-response') === null) {
            hello(site).api('me').then(function(json) {

              var button = document.getElementById(site + 'Button');
              button.innerHTML = 'Logged into ' + site.charAt(0).toUpperCase() + site.slice(1);

			  // replace generic banner with personalized greeting
			  constructGreetingBanner(json.name);

			  // store username for later
			  localStorage['sinai-scholars-username'] = json.name;
			  
			  setTimeout(function() { $('#hide-login').click(); }, 1000);
            });
          }

          console.log('Signed into ' + site);
        } else {
          console.log('Server response: ' + this.responseText);
        }
      };

      xhr.send('token=' + session.access_token + '&site=' + site);
    }, function(e) {
      console.log('Login error: ' + e.error.message);
    }
  );
}
