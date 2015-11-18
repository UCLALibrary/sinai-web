$("#about").mouseenter(function() {
	 $("#sub-navbar-container").css("visibility", "visible");
	 
}).mouseleave(function() {
	var sub_navbar = $("#sub-navbar-container");

	if (sub_navbar.is(":hover") || $("#main-navbar").is(":hover")) {
		return;
	}
	sub_navbar.css("visibility", "hidden");
});

$("#sub-navbar-container").mouseleave(function() {
	if ($("#about").is(":hover")) {
		return;
	}
	$(this).css("visibility", "hidden");
});

$("#main-navbar").mouseleave(function() {
	if ($("#sub-navbar-container").is(":hover")) {
		return;
	}
	$("#sub-navbar-container").css("visibility", "hidden");
});
