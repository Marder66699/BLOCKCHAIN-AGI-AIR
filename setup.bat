@echo off
REM AI ON BLOCKCHAIN - Setup Script
REM Author: Sir Charles Spikes
REM Contact: SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI

echo ===============================================
echo AI ON BLOCKCHAIN - PROJECT SETUP
echo By Sir Charles Spikes
echo Cincinnati, Ohio
echo ===============================================
echo.

REM Check for Node.js
echo Checking for Node.js...
node --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Node.js not found! Please install Node.js first.
    echo Download from: https://nodejs.org/
    pause
    exit /b 1
)
echo Node.js found!
echo.

REM Check for Java
echo Checking for Java...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java not found! Please install Java 17 or later.
    echo Download from: https://adoptium.net/
    pause
    exit /b 1
)
echo Java found!
echo.

REM Install Node dependencies
echo Installing Node.js dependencies...
call npm install
if %errorlevel% neq 0 (
    echo ERROR: Failed to install Node.js dependencies
    pause
    exit /b 1
)
echo.

REM Download Gradle Wrapper
echo Setting up Gradle...
if not exist gradlew.bat (
    echo Downloading Gradle Wrapper...
    powershell -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.5-bin.zip' -OutFile 'gradle.zip'"
    powershell -Command "Expand-Archive -Path 'gradle.zip' -DestinationPath '.'"
    del gradle.zip
    
    REM Create gradle wrapper
    gradle-8.5\bin\gradle.bat wrapper --gradle-version 8.5
    rmdir /s /q gradle-8.5
)
echo Gradle setup complete!
echo.

REM Generate Protocol Buffers
if exist gradlew.bat (
    echo Generating Protocol Buffer classes with PBJ...
    call gradlew.bat generatePbj
    if %errorlevel% neq 0 (
        echo WARNING: Protocol buffer generation failed. You may need to run this manually.
    )
)
echo.

REM Compile contracts
echo Compiling smart contracts...
call npx hardhat compile
if %errorlevel% neq 0 (
    echo WARNING: Contract compilation failed. Check your Solidity code.
)
echo.

REM Create .env file if it doesn't exist
if not exist .env (
    echo Creating .env file from template...
    copy .env.example .env
    echo Please edit .env file with your configuration!
)
echo.

echo ===============================================
echo SETUP COMPLETE!
echo ===============================================
echo.
echo Next steps:
echo 1. Edit .env file with your private key and RPC URLs
echo 2. Run 'npm run node' to start local blockchain
echo 3. Run 'npm run deploy:localhost' to deploy contracts
echo 4. Run 'gradlew run' to start AI node with PBJ/GRPC
echo.
echo Contact: SirCharlesspikes5@gmail.com
echo Telegram: @SirGODSATANAGI
echo ===============================================
pause
