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
          /*
          var loginLink = document.getElementById('login');

          if (loginLink !== null) {
            loginLink.text = "Logout";
            loginLink.setAttribute('href', '/logout');
          }
          */

          // Let's give more of a visual representation of being logged in
          if (document.getElementById(site + '-login-response') === null) {
            hello(site).api('me').then(function(json) {

              var loginTd = document.getElementById(site + '-login');
              var userImage = document.createElement('img');
              var userName = document.createTextNode(' ' + json.name);
              var divElement = document.createElement('div');
              var button = document.getElementById(site + 'Button');

			  var bannerText = document.getElementById('greeting');
			  var logoutLink = document.createElement('a');
			  var nameStrongText = document.createElement('b');
			  var linkStrongText = document.createElement('b');

              divElement.setAttribute('style', 'margin-top: 10px;');
              userImage.setAttribute('src', json.thumbnail);
              divElement.setAttribute('id', site + '-login-response');

              divElement.appendChild(userImage);
              divElement.appendChild(userName);

              button.innerHTML = 'Logged into ' + site.charAt(0).toUpperCase() + site.slice(1);
              loginTd.appendChild(divElement); 



			  nameStrongText.innerHTML = json.name;
			  linkStrongText.innerHTML = 'logout';

			  logoutLink.setAttribute('href', '/logout');
			  logoutLink.appendChild(linkStrongText);

              bannerText.innerHTML = 'welcome, ';
			  bannerText.appendChild(nameStrongText);
			  bannerText.appendChild(document.createElement('br'));
			  bannerText.appendChild(logoutLink);
			  
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
