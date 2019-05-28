<?php
	
	if($_SERVER['REQUEST_METHOD']== 'GET'){
		$fr = fopen("card.txt","r");
		$textEncoded = fread($fr,filesize("card.txt"));
		$data = json_decode($textEncoded,true);
		$argsData = json_encode($data["data"],JSON_FORCE_OBJECT);
		if(isset($_GET['Write'])){
			$response = exec("sudo python3 /home/pi/Documents/MecinFC/writerRF.py " . base64_encode($argsData));
			var_dump($response);
		}
		echo "<form method='GET'>";
		echo "<input type='submit' name='Write'>";
		echo "</form>";
	}
	else if($_SERVER['REQUEST_METHOD'] == 'POST'){
		$fw = fopen("card.txt","w");
		$text = $_POST['testing'];
		echo $text;
		fwrite($fw,$text);
		fclose($fw);
	}
?>
