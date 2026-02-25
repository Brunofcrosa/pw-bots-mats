#Region ;**** Directives created by AutoIt3Wrapper_GUI ****
#AutoIt3Wrapper_icon=..\..\..\..\..\Arquivos de programas\Microsoft Visual Studio .NET 2003\Common7\Graphics\icons\Elements\EARTH.ICO
#EndRegion ;**** Directives created by AutoIt3Wrapper_GUI ****
;********************************************************************************
;* AutoIt Script v3.x                                                           *
;* Project: MHS-Bot-PWBR                                                        *
;* Description: Bot Engine for Perfect World Game (LUG BR Oficial Server)       *
;* Author: MHS                                                                  *
;********************************************************************************

#include <NomadMemory.au3>
#include <Memory.au3>
#include <GUIConstants.au3>
#include <Array.au3>

;********************************************************************************
;* Options                                                                      *
;********************************************************************************
Opt("GUIOnEventMode", 1)
Opt("SendKeyDownDelay", 35)
HotKeySet("{F9}","StartOrStop")


;********************************************************************************
;* Global Software Control Information                                          *
;********************************************************************************
Global $SOFTWARE_TITLE = "MHS-Bot-PW"
Global $SOFTWARE_VERSION = "v2.3 Mem Custom"
Global $SOFTWARE_CONFIG = "MHS-Bot-PW-Custom.ini"
Global $STOP = True
Global $CURRENT_MOB_LIST[10]
Global $MOB_LIST_COUNT = "0"
Global $TARGET_STATE
Global $TARGET_ID
Global $LAST_KILLEDTIME
Global $LAST_KILLED
Global $KILL_STATE = 0
Global $KILLS_COUNT = 0
Global $ACTIVE_SKILL = 0
Global $ACTIVE_WEAPONS = 0
Global $TIMER_CHANGE_WEAPON
Global $TID = 0

Global $SKILL_DELAY_CHECK[10]

Global $HP = "0"
Global $MP = "0"
Global $MAXHP = "0"
Global $MAXMP = "0"
Global $HP_PERC = "0"
Global $MP_PERC = "0"
Global $EXP = "0"
Global $LAST_HP_READ = "0"

Global $BUFFS_TIMER[9], $BUFFS_TIMER_DIFF[9]

Global $CFG_SKILLS_ROOT_KEY = "Skills"
Global $CFG_SKILL_UBOUND_KEY = "SkillUbound"
Global $CFG_SKILL_COMBO_KEY = "SkillComboKey"
Global $CFG_SKILL_DELAY_KEY = "SkillDelay"
Global $SKCOUNTCFG = IniRead($SOFTWARE_CONFIG, $CFG_SKILLS_ROOT_KEY, $CFG_SKILL_UBOUND_KEY, "1")

Global $CFG_BUFFS_ROOT_KEY = "Buffs"
Global $CFG_BUFFS_FLAG_KEY = "AutoBuffsFlag"
Global $CFG_BUFFS_UBOUND_KEY = "BuffsUbound"
Global $CFG_BUFFS_COMBO_KEY = "BuffsComboKey"
Global $CFG_BUFFS_DELAY_KEY = "BuffsDelay"
Global $CFG_BUFFS_FREQUENCY_KEY = "BuffsFrequency"
Global $SKCOUNTCFG_BUFFS = IniRead($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_UBOUND_KEY, "1")

Global $CFG_WEAPONS_ROOT_KEY = "ChangeWeapons"
Global $CFG_WEAPONS_FLAG_KEY = "ChangeWeaponsFlag"
Global $CFG_WEAPONS_UBOUND_KEY = "ChangeWeaponsUbound"
Global $CFG_WEAPONS_COMBO_KEY = "ChangeWeaponsComboKey"
Global $CFG_WEAPONS_DELAY_KEY = "ChangeWeaponsDelay"
Global $SKCOUNTCFG_WEAPONS = IniRead($SOFTWARE_CONFIG, $CFG_WEAPONS_ROOT_KEY, $CFG_WEAPONS_UBOUND_KEY, "1")

Global $CFG_MOBLIST_ROOT_KEY = "MobList"
Global $CFG_MOBLIST_UBOUND_KEY = "MobListUbound"
Global $CFG_MOBLIST_MONSTER_KEY = "Monster"

Global $CFG_HEAL_ROOT_KEY = "Heal"
Global $CFG_HEAL_AUTOREST_HP_KEY = "AutoRestHP"
Global $CFG_HEAL_AUTOREST_HP_PERC_KEY = "AutoRestHPPerc"
Global $CFG_HEAL_AUTOREST_MP_KEY = "AutoRestMP"
Global $CFG_HEAL_AUTOREST_MP_PERC_KEY = "AutoRestMPPerc"
Global $CFG_HEAL_AUTOREST_KEY = "AutoRestKey"

Global $CFG_HEAL_AUTOPOT_HP_FLAG_KEY = "AutoPotFlagHP"
Global $CFG_HEAL_AUTOPOT_HP_PERC_KEY = "AutoPotHPPerc"
Global $CFG_HEAL_AUTOPOT_MP_FLAG_KEY = "AutoPotFlagMP"
Global $CFG_HEAL_AUTOPOT_MP_PERC_KEY = "AutoPotMPPerc"
Global $CFG_HEAL_AUTOPOT_HP_KEY = "AutoPotHPKey"
Global $CFG_HEAL_AUTOPOT_MP_KEY = "AutoPotMPKey"
Global $CFG_HEAL_STOP_ON_DIE_KEY = "StopOnDie"

Global $CFG_LOOT_ROOT_KEY = "Loot"
Global $CFG_LOOT_FLAG_KEY = "LootFlag"
Global $CFG_LOOT_TIMES_KEY = "Times"
Global $CFG_LOOT_KEY = "LootKey"

Global $CFG_FLYESCAPE_ROOT_KEY = "FlyEscape"
Global $CFG_FLYESCAPE_FLAG_KEY = "FlyEscapeFlag"
Global $CFG_FLYESCAPE_KEY = "FlyEscapeKey"
Global $CFG_FLYESCAPE_DAMAGE_KEY = "FlyEscapeDamage"
Global $CFG_FLYESCAPE_SPACE_KEY = "FlyEscapeTotalSpaces"

;Off-Sets Variables
Global $SOFTWARE_OFFSET_CONFIG = "Custom_OffSets.ini"
Global $CFG_OFFSET_ROOT_KEY = "Custom_32_Offsets_In_Decimal"
Global $CFG_OFFSET_AT = "Target_OffSet"
Global $CFG_OFFSET_HP = "HP_OffSet"
Global $CFG_OFFSET_MAXHP = "MaxHP_OffSet"
Global $CFG_OFFSET_MP = "MP_OffSet"
Global $CFG_OFFSET_MAXMP = "MaxMP_OffSet"

Global $CFG_BASEADDRESS_ROOT_KEY = "Perfect_World_Base_Address_In_Decimal"
Global $CFG_BASEADDRESS_APP_KEY = "Application_Title"
Global $CFG_BASEADDRESS_KEY = "Base_Address"

IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_BASEADDRESS_ROOT_KEY, $CFG_BASEADDRESS_APP_KEY, IniRead($SOFTWARE_OFFSET_CONFIG, $CFG_BASEADDRESS_ROOT_KEY, $CFG_BASEADDRESS_APP_KEY, "Element Client"))
IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_BASEADDRESS_ROOT_KEY, $CFG_BASEADDRESS_KEY, Dec(Hex(IniRead($SOFTWARE_OFFSET_CONFIG, $CFG_BASEADDRESS_ROOT_KEY, $CFG_BASEADDRESS_KEY, "9400796"))))

IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_AT, IniRead($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_AT, Dec("A08")))
IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_HP, IniRead($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_HP, "1104"))
IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MAXHP, IniRead($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MAXHP, "1144"))
IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MP, IniRead($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MP, "1108"))
IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MAXMP, IniRead($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MAXMP, "1148"))

Global $APP_BASE_ADDRESS = "0x" & Hex(IniRead($SOFTWARE_OFFSET_CONFIG, $CFG_BASEADDRESS_ROOT_KEY, $CFG_BASEADDRESS_KEY, "9400796"))

Global $OFFSET_AT[3]
$OFFSET_AT[1] = 32
$OFFSET_AT[2] = IniRead($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_AT, Dec("A08"))
Global $OFFSET_MP[3], $OFFSET_MAX_MP[3]
$OFFSET_MP[1] = 32
$OFFSET_MP[2] = IniRead($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MP, "1108")
$OFFSET_MAX_MP[1] = 32
$OFFSET_MAX_MP[2] = IniRead($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MAXMP, "1148")
Global $OFFSET_HP[3], $OFFSET_MAX_HP[3]
$OFFSET_HP[1] = 32
$OFFSET_HP[2] = IniRead($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_HP, "1104")
$OFFSET_MAX_HP[1] = 32
$OFFSET_MAX_HP[2] = IniRead($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MAXHP, "1144")

;Process Variables
Global $APP_TITLE = IniRead($SOFTWARE_OFFSET_CONFIG, $CFG_BASEADDRESS_ROOT_KEY, $CFG_BASEADDRESS_APP_KEY, "Element Client")
Global $PROCESS_ID = WinGetProcess($APP_TITLE)
Global $PROCESS_INFORMATION = _MemoryOpen($PROCESS_ID)

If @error Then
	MsgBox(0, "Can´t Find Perfect World", "Impossible to detect your Perfect World. Review settings in " & $SOFTWARE_OFFSET_CONFIG & ". Set the correct value for " & $CFG_BASEADDRESS_APP_KEY & " and for " & $CFG_BASEADDRESS_KEY & " properties.")
	Exit
EndIf

;********************************************************************************
;* Main Form Design                                                             *
;********************************************************************************
$FORM_MAIN = GUICreate($SOFTWARE_TITLE & " " & $SOFTWARE_VERSION, 260, 590, 10, 10, -1, -1)
GUISetOnEvent($GUI_EVENT_CLOSE, "WindowCloseClicked")

;Button and Event To Configure Skills
$BUTTON_START = GUICtrlCreateButton("Start", 5, 5, 60, 45)
GUICtrlSetOnEvent(-1, "StartOrStop")

$BUTTON_WEAPONS = GUICtrlCreateButton("Weapons", 5, 55, 60, 20)
GUICtrlSetOnEvent(-1, "SetChangeWeapons")

;Button and Event To Configure Skills
$BUTTON_SKILLS = GUICtrlCreateButton("Skills", 70, 5, 90, 20)
GUICtrlSetOnEvent(-1, "SetSkills")

;Button and Event To Configure Auto-Pot & Rest
$BUTTON_POT_REST = GUICtrlCreateButton("Auto-Pot/Rest", 70, 30, 90, 20)
GUICtrlSetOnEvent(-1, "SetAutoPotRest")

;Button and Event To Reset Entered Mob-List
$BUTTON_POT_REST = GUICtrlCreateButton("Reset Mob-List", 165, 5, 90, 20)
GUICtrlSetOnEvent(-1, "ResetMobList")

;Button and Event To Set New Mob-List
$BUTTON_POT_REST = GUICtrlCreateButton("Set Mob-List", 165, 30, 90, 20)
GUICtrlSetOnEvent(-1, "SetMobList")

;Button and Event To Reset Entered Mob-List
$BUTTON_AUTO_BUFF = GUICtrlCreateButton("Auto-Buffs", 70, 55, 90, 20)
GUICtrlSetOnEvent(-1, "SetAutoBuffs")

;Button and Event To Set New Mob-List
$BUTTON_FLY_ESCAPE = GUICtrlCreateButton("Fly Escape", 165, 55, 90, 20)
GUICtrlSetOnEvent(-1, "SetFlyEscape")

$LABEL_LIST_MOB = GUICtrlCreateLabel("Current Mob-List (Max: 10)", 5, 80, 250, 20)
$LIST_MOB = GUICtrlCreateListView("Priority|Mob ID", 5, 95, 250, 100)
RefreshMobList()

$GROUP_BOT_INFO = GUICtrlCreateGroup(" Bot Info ", 5, 200, 250, 65)

$LABEL_BOT_STATUS = GUICtrlCreateLabel("Status: Stopped", 15, 220, 150, 20)
$LABEL_KILLED_STATUS = GUICtrlCreateLabel("Total Killed: 0", 140, 220, 100, 20)
$LABEL_GENERAL_STATUS = GUICtrlCreateLabel("Action: Nothing", 15, 240, 200, 20)
$LABEL_WEAPON = GUICtrlCreateLabel("Weapon: 0", 140, 240, 80, 20)

$GROUP_CHAR_INFO = GUICtrlCreateGroup(" Char Info ", 5, 265, 250, 65)

$LABEL_HP_STATUS = GUICtrlCreateLabel("HP: 0/0 (0%)", 15, 285, 150, 20)
$LABEL_MP_STATUS = GUICtrlCreateLabel("MP: 0/0 (0%)", 135, 285, 115, 20)

$LABEL_DAMAGE = GUICtrlCreateLabel("Damage During Atack: Undefined", 15, 305, 220, 20)

$GROUP_OFFSET_INFO = GUICtrlCreateGroup(" Memory OffSets (CustomOffSets.ini) ", 5, 330, 250, 255)

$LABEL_OFFSET_TARGET = GUICtrlCreateLabel("Target: ", 15, 350, 50, 20) 
$LABEL_OFFSET_HP = GUICtrlCreateLabel("HP", 15, 370, 50, 20) 
$LABEL_OFFSET_MAXHP = GUICtrlCreateLabel("Max HP", 15, 390,50, 20) 
$LABEL_OFFSET_MP = GUICtrlCreateLabel("MP", 15, 410, 50, 20) 
$LABEL_OFFSET_MAXMP = GUICtrlCreateLabel("Max MP", 15, 430, 50, 20) 

$LABEL_OFFSETH_TARGET = GUICtrlCreateLabel("Hex: " & Hex($OFFSET_AT[2]), 80, 350, 85, 20) 
$LABEL_OFFSETH_HP = GUICtrlCreateLabel("Hex: " & Hex($OFFSET_HP[2]), 80, 370, 85, 20) 
$LABEL_OFFSETH_MAXHP = GUICtrlCreateLabel("Hex: " & Hex($OFFSET_MAX_HP[2]), 80, 390, 85, 20) 
$LABEL_OFFSETH_MP = GUICtrlCreateLabel("Hex: " & Hex($OFFSET_MP[2]), 80, 410, 85, 20) 
$LABEL_OFFSETH_MAXMP = GUICtrlCreateLabel("Hex: " & Hex($OFFSET_MAX_MP[2]), 80, 430, 85, 20) 

$LABEL_OFFSETV_TARGET = GUICtrlCreateLabel("Dec: " & $OFFSET_AT[2], 180, 350, 50, 20) 
$LABEL_OFFSETV_HP = GUICtrlCreateLabel("Dec: " & $OFFSET_HP[2], 180, 370, 50, 20) 
$LABEL_OFFSETV_MAXHP = GUICtrlCreateLabel("Dec: " & $OFFSET_MAX_HP[2], 180, 390, 50, 20) 
$LABEL_OFFSETV_MP = GUICtrlCreateLabel("Dec: " & $OFFSET_MP[2], 180, 410, 50, 20) 
$LABEL_OFFSETV_MAXMP = GUICtrlCreateLabel("Dec: " & $OFFSET_MAX_MP[2], 180, 430, 50, 20) 

$LABEL_APP_TITLE = GUICtrlCreateLabel("App Title: " & $APP_TITLE, 15, 450, 200, 20) 
$LABEL_BASE_ADDRESS = GUICtrlCreateLabel("Hex Base Address: " & $APP_BASE_ADDRESS, 15, 470, 200, 20) 

$LABEL_OFFSET_TIP = GUICtrlCreateLabel("You have to search for memory base address and these offsets addresses using memory searchers like CE (Cheat Engine) and update the INI file with your own values. Default is BR.", 15, 495, 230, 50) 

$BUTTON_SET_OFFSET_DEFAULT = GUICtrlCreateButton("[BR]", 15, 555, 50, 20) 
GUICtrlSetOnEvent(-1, "SetOffSetDefaultBrazil")

$BUTTON_SET_OFFSET_DEFAULT = GUICtrlCreateButton("[MYEN]", 70, 555, 50, 20) 
GUICtrlSetOnEvent(-1, "SetOffSetDefaultMYEN")

$BUTTON_SET_OFFSET_DEFAULT = GUICtrlCreateButton("[RU]", 125, 555, 50, 20) 
GUICtrlSetOnEvent(-1, "SetOffSetDefaultRU")

$BUTTON_SET_OFFSET_DEFAULT = GUICtrlCreateButton("[INDO]", 180, 555, 50, 20) 
GUICtrlSetOnEvent(-1, "SetOffSetDefaultIndo")

;********************************************************************************
;* Main Loop                                                                    *
;********************************************************************************
;Show The Main Form
GUISetState(@SW_SHOW, $FORM_MAIN)

Do
	Sleep(1000)
	GUICtrlSetData($LABEL_BOT_STATUS, "Status: Stopped")
	GUICtrlSetData($LABEL_GENERAL_STATUS, "Action: Nothing")
	UpdateCharInfo()
	TimerBuffsStart()
	
	$ACTIVE_WEAPONS = 0
	$TIMER_CHANGE_WEAPON = TimerInit()
	
Until $STOP = False
While (1)

	UpdateCharInfo()
	Main()

WEnd

;********************************************************************************
;* Generic Methods & Events                                                     *
;********************************************************************************

Func SetOffSetDefaultBrazil()

	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_BASEADDRESS_ROOT_KEY, $CFG_BASEADDRESS_APP_KEY, "Element Client")
	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_BASEADDRESS_ROOT_KEY, $CFG_BASEADDRESS_KEY, "9400796")

	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_AT, "2568")
	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_HP, "1104")
	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MAXHP, "1144")
	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MP, "1108")
	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MAXMP, "1148")

	MsgBox(0, "Restart", "Settings adjusted to BR (Brazil). The application will be closed. After that, restart this application again!")
	Exit

EndFunc

Func SetOffSetDefaultMYEN()

	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_BASEADDRESS_ROOT_KEY, $CFG_BASEADDRESS_APP_KEY, "Element Client")
	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_BASEADDRESS_ROOT_KEY, $CFG_BASEADDRESS_KEY, "9451524")

	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_AT, "2584")
	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_HP, "1104")
	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MAXHP, "1144")
	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MP, "1108")
	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MAXMP, "1148")

	MsgBox(0, "Restart", "Settings adjusted to MY-EN. The application will be closed. After that, restart this application again!")
	Exit

EndFunc

Func SetOffSetDefaultRU()

	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_BASEADDRESS_ROOT_KEY, $CFG_BASEADDRESS_APP_KEY, "Element Client")
	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_BASEADDRESS_ROOT_KEY, $CFG_BASEADDRESS_KEY, "9589892")

	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_AT, "2584")
	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_HP, "1104")
	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MAXHP, "1144")
	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MP, "1108")
	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MAXMP, "1148")

	MsgBox(0, "Restart", "Settings adjusted to RU (Russia). The application will be closed. After that, restart this application again!")
	Exit

EndFunc

Func SetOffSetDefaultIndo()

	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_BASEADDRESS_ROOT_KEY, $CFG_BASEADDRESS_APP_KEY, "Element Client")
	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_BASEADDRESS_ROOT_KEY, $CFG_BASEADDRESS_KEY, "9585732")

	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_AT, "2584")
	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_HP, "1104")
	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MAXHP, "1144")
	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MP, "1108")
	IniWrite($SOFTWARE_OFFSET_CONFIG, $CFG_OFFSET_ROOT_KEY, $CFG_OFFSET_MAXMP, "1148")

	MsgBox(0, "Restart", "Settings adjusted to Indonesia. The application will be closed. After that, restart this application again!")
	Exit

EndFunc


Func Main()


	If $STOP = False Then
		
		GUICtrlSetData($LABEL_BOT_STATUS, "BOT Status: Started")

		$TARGET_STATE = _MemoryPointerRead($APP_BASE_ADDRESS, $PROCESS_INFORMATION, $OFFSET_AT)

		If Not @error Then

			TargetMob()
			
			If $TARGET_STATE[1] <> 0 Then
				KillTarget()
			EndIf
			
			UpdateCharInfo()
			StopCheck()
			HPMPRestoreCheck()
			
		EndIf

	Else
		Sleep(1000)
		GUICtrlSetData($LABEL_BOT_STATUS, "BOT Status: Stopped")
		GUICtrlSetData($LABEL_GENERAL_STATUS, "Action: Nothing")
		TimerBuffsStart()
		ResetSkillDelays()
		
		$ACTIVE_WEAPONS = 0
		$TIMER_CHANGE_WEAPON = TimerInit()		
		
	EndIf
	
EndFunc   ;==>Main

Func WindowCloseClicked()
	Exit
EndFunc   ;==>WindowCloseClicked

Func WindowSkillCloseClicked()
	GUIDelete($FORM_SKILL)
EndFunc   ;==>WindowSkillCloseClicked

Func WindowAutoPotRestCloseClicked()
	GUIDelete($FORM_AUTOPOTREST)
EndFunc   ;==>WindowAutoPotRestCloseClicked

Func WindowFlyEscapeCloseClicked()
	GUIDelete($FORM_FLYESCAPE)
EndFunc

Func WindowAutoBuffCloseClicked()
	GUIDelete($FORM_BUFFS)
EndFunc

Func WindowChangeWeaponsCloseClicked()
	GUIDelete($FORM_WEAPONS)
EndFunc

Func ResetSkillDelays()
	
	$SKILL_DELAY_CHECK[1] = TimerInit()
	$SKILL_DELAY_CHECK[2] = TimerInit()
	$SKILL_DELAY_CHECK[3] = TimerInit()
	$SKILL_DELAY_CHECK[4] = TimerInit()
	$SKILL_DELAY_CHECK[5] = TimerInit()
	$SKILL_DELAY_CHECK[6] = TimerInit()
	$SKILL_DELAY_CHECK[7] = TimerInit()
	$SKILL_DELAY_CHECK[8] = TimerInit()
	$SKILL_DELAY_CHECK[9] = TimerInit()
	
EndFunc

Func UpdateCharInfo()

	$HP = _MemoryPointerRead($APP_BASE_ADDRESS, $PROCESS_INFORMATION, $OFFSET_HP)
	$MAXHP = _MemoryPointerRead($APP_BASE_ADDRESS, $PROCESS_INFORMATION, $OFFSET_MAX_HP)

	If Not @error Then
		$HP_PERC = $HP[1] / $MAXHP[1] * 100
		GUICtrlSetData($LABEL_HP_STATUS, "HP: " & $HP[1] & "/" & $MAXHP[1] & " (" & Int($HP_PERC) & "%)")
	EndIf

	$MP = _MemoryPointerRead($APP_BASE_ADDRESS, $PROCESS_INFORMATION, $OFFSET_MP)
	$MAXMP = _MemoryPointerRead($APP_BASE_ADDRESS, $PROCESS_INFORMATION, $OFFSET_MAX_MP)

	If Not @error Then
		$MP_PERC = $MP[1] / $MAXMP[1] * 100
		GUICtrlSetData($LABEL_MP_STATUS, "MP: " & $MP[1] & "/" & $MAXMP[1] & " (" & Int($MP_PERC) & "%)")
	EndIf
	
EndFunc   ;==>UpdateCharInfo

Func TargetMob()

	HPMPAutoPotCheck()
	AutoBuffsCheck()
	ResetSkillDelays()
	ChangeWeaponsCheck()

	$TARGET_MOB = PreferredMob(1)
	GUICtrlSetData($LABEL_GENERAL_STATUS, "Action: Finding Target")

	Do
		GUICtrlSetData($LABEL_GENERAL_STATUS, "Action: Trying Mob" & $TARGET_MOB)
		
		$TARGET_STATE = _MemoryPointerRead($APP_BASE_ADDRESS, $PROCESS_INFORMATION, $OFFSET_AT)
		
		If $TARGET_STATE[1] = 0 Then
			
			$TID = IniRead($SOFTWARE_CONFIG, $CFG_MOBLIST_ROOT_KEY, $CFG_MOBLIST_MONSTER_KEY & $TARGET_MOB, "")
			
			_MemoryPointerWrite($APP_BASE_ADDRESS, $PROCESS_INFORMATION, $OFFSET_AT, "0x" & $TID)

			If Not @error Then
				
				Sleep(200)
				$TARGET_STATE = _MemoryPointerRead($APP_BASE_ADDRESS, $PROCESS_INFORMATION, $OFFSET_AT)
				
				If $TARGET_STATE[1] = 0 Then
					$TARGET_MOB = PreferredMob($TARGET_MOB + 1)
				EndIf
				
			EndIf
			
		EndIf
		
		$TARGET_STATE = _MemoryPointerRead($APP_BASE_ADDRESS, $PROCESS_INFORMATION, $OFFSET_AT)
		
	Until $TARGET_STATE[1] <> 0 Or $TARGET_MOB > IniRead($SOFTWARE_CONFIG, $CFG_MOBLIST_ROOT_KEY, $CFG_MOBLIST_UBOUND_KEY, 1)
	
EndFunc   ;==>TargetMob

Func PreferredMob($MOBID)

	Local $MOBRET = $MOBID
	
	GUICtrlSetData($LABEL_GENERAL_STATUS, "Action: Trying Mob " & $MOBRET)
	
	If $KILL_STATE <> 0 Then
		
		$TID = IniRead($SOFTWARE_CONFIG, $CFG_MOBLIST_ROOT_KEY, $CFG_MOBLIST_MONSTER_KEY & $MOBRET, "")
		
		If $TID = Hex($KILL_STATE) And TimerDiff($LAST_KILLEDTIME) < 10 * 1000 Then
			$MOBRET = $MOBRET + 1
		EndIf
	EndIf

	Return $MOBRET

EndFunc   ;==>PreferredMob

Func KillTarget()

	;Read Last HP Info
	UpdateCharInfo()
	$LAST_HP_READ = $HP[1]

	$ACTIVE_SKILL = 0
	$TS = TimerInit()
	
	$TARGET_STATE = _MemoryPointerRead($APP_BASE_ADDRESS, $PROCESS_INFORMATION, $OFFSET_AT)
	
	If Not @error And $TARGET_STATE[1] <> 0 Then
		
		$KILL_STATE = $TARGET_STATE[1]
		
		Do
			;Set the Active Skill
			$ACTIVE_SKILL = $ACTIVE_SKILL + 1
			
			If $ACTIVE_SKILL > $SKCOUNTCFG Then
				$ACTIVE_SKILL = 1
			EndIf

			;Check The Last Time Used The Active Skill (Delay Configured to Each Skill)
			$w8 = IniRead($SOFTWARE_CONFIG, $CFG_SKILLS_ROOT_KEY, $CFG_SKILL_DELAY_KEY & $ACTIVE_SKILL, "1")
			
			If TimerDiff($SKILL_DELAY_CHECK[$ACTIVE_SKILL]) > ($w8 * 1000) Then

				;Update the Timer to Active Skill
				$SKILL_DELAY_CHECK[$ACTIVE_SKILL] = TimerInit()

				;Send the Skill Command to Game
				$key = IniRead($SOFTWARE_CONFIG, $CFG_SKILLS_ROOT_KEY, $CFG_SKILL_COMBO_KEY & $ACTIVE_SKILL, "{F1}")
				ControlSend($APP_TITLE, "", "", $key)
				
			EndIf

			;Check if Auto-Pot is Needed
			HPMPAutoPotCheck()

			;Check if Fly-Escape is Needed
			CheckFlyEscape()
			
			;Identify The Monster In The List
			$k = 0

			Do
				$k = $k + 1
				If Hex($KILL_STATE) = IniRead($SOFTWARE_CONFIG, $CFG_MOBLIST_ROOT_KEY, $CFG_MOBLIST_MONSTER_KEY & $k, "") Then
					GUICtrlSetData($LABEL_GENERAL_STATUS, "Action: Killing Monster " & $k)
					ExitLoop
				EndIf
			Until $k > IniRead($SOFTWARE_CONFIG, $CFG_MOBLIST_ROOT_KEY, $CFG_MOBLIST_UBOUND_KEY, 1)
			
			;Update the Target State
			$TARGET_STATE = _MemoryPointerRead($APP_BASE_ADDRESS, $PROCESS_INFORMATION, $OFFSET_AT)
			$TIMER = TimerDiff($TS)

		Until $KILL_STATE <> $TARGET_STATE[1] Or $TIMER > 2 * 60000 Or $TARGET_STATE[1] = 0 ;or DieChk() = 1

		$LAST_KILLED = $k
		$LAST_KILLEDTIME = TimerInit()
		$KILLS_COUNT = $KILLS_COUNT + 1
		
		GUICtrlSetData($LABEL_KILLED_STATUS, "Total Killed: " & $KILLS_COUNT)
		PickLoot(IniRead($SOFTWARE_CONFIG, $CFG_LOOT_ROOT_KEY, $CFG_LOOT_TIMES_KEY, "1"))
		
	EndIf
	
EndFunc   

Func StopCheck()
	
	If  IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_STOP_ON_DIE_KEY, 0) = 1 Then
		If $HP[1] = 0 Then
			StartOrStop()
		EndIf
	EndIf	
			
EndFunc

Func PickLoot($MAX)

	If IniRead($SOFTWARE_CONFIG, $CFG_LOOT_ROOT_KEY, $CFG_LOOT_FLAG_KEY, "0") = 1 Then
		For $P = $MAX To 1 Step -1
			$PKEY = IniRead($SOFTWARE_CONFIG, $CFG_LOOT_ROOT_KEY, $CFG_LOOT_KEY, "0")
			ControlSend($APP_TITLE, "", "", $PKEY)
			Sleep(300)
		Next
	EndIf
	
EndFunc

Func HPMPAutoPotCheck()

	If IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOPOT_HP_FLAG_KEY, 0) = 1 Then
		If Int ($HP_PERC) < Int(IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOPOT_HP_PERC_KEY, 1)) Then
			ControlSend($APP_TITLE, "", "", IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOPOT_HP_KEY, ""))
		Endif
	EndIf
	
	If IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOPOT_MP_FLAG_KEY, 0) = 1 Then
		If Int ($MP_PERC) < Int(IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOPOT_MP_PERC_KEY, 1)) Then
			ControlSend($APP_TITLE, "", "", IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOPOT_MP_KEY, ""))
		Endif
	EndIf
	
EndFunc

Func AutoBuffsCheck()
	
	If IniRead($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_FLAG_KEY, "0") = 1 Then	
	
		For $Q = 1 To IniRead($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_UBOUND_KEY, "1") Step +1
			$BUFFS_TIMER_DIFF[$Q] = TimerDiff($BUFFS_TIMER[$Q])
		Next
		
		For $Q = 1 To IniRead($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_UBOUND_KEY, "1") Step +1
			If $BUFFS_TIMER_DIFF[$Q] / 60000 > IniRead($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_FREQUENCY_KEY & $Q, "1") Then
				SendBuff($Q)
				;$BUFFSCOUNT = $BUFFSCOUNT + 1
				$BUFFS_TIMER[$Q] = TimerInit()
			EndIf	
		Next
	EndIf	
	
EndFunc

Func ChangeWeaponsCheck()

	If IniRead($SOFTWARE_CONFIG, $CFG_WEAPONS_ROOT_KEY, $CFG_WEAPONS_FLAG_KEY, "0") = 1 Then	
		If TimerDiff($TIMER_CHANGE_WEAPON) / 60000 > IniRead($SOFTWARE_CONFIG, $CFG_WEAPONS_ROOT_KEY, $CFG_WEAPONS_DELAY_KEY, "180") Then
			$ACTIVE_WEAPONS = $ACTIVE_WEAPONS + 1
			
			IF $ACTIVE_WEAPONS <= IniRead($SOFTWARE_CONFIG, $CFG_WEAPONS_ROOT_KEY, $CFG_WEAPONS_UBOUND_KEY, "1") Then
				$KEY_WEAPON = IniRead($SOFTWARE_CONFIG, $CFG_WEAPONS_ROOT_KEY, $CFG_WEAPONS_COMBO_KEY & $ACTIVE_WEAPONS, "")
				ControlSend($APP_TITLE, "", "", $KEY_WEAPON)
				$TIMER_CHANGE_WEAPON = TimerInit()
				GUICtrlSetData($LABEL_WEAPON, "Weapon: " & $ACTIVE_WEAPONS)
			Else
				$TIMER_CHANGE_WEAPON = TimerInit()
				GUICtrlSetData($LABEL_WEAPON, "Weapon: Last")
			EndIf
		EndIf
	EndIf
	
EndFunc

Func SendBuff($BUFF_SEQ)

	$KEY = IniRead($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_COMBO_KEY & $BUFF_SEQ, "0")
	ControlSend($APP_TITLE, "", "", $KEY)
	Sleep(Int(IniRead($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_DELAY_KEY & $BUFF_SEQ, "0")) * 1000)
	
EndFunc

Func TimerBuffsStart()

	If IniRead($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_FLAG_KEY, "0") = 1 Then
		For $Q = 1 to IniRead($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_UBOUND_KEY, "1") Step +1
			$BUFFS_TIMER[$Q] = TimerInit()
		Next
	EndIf	
	
EndFunc

Func CheckFlyEscape()
	
	UpdateCharInfo()
	Local $POTENTIAL_DAMAGE = Int(Int($LAST_HP_READ) - Int($HP[1]))
	
	If ($POTENTIAL_DAMAGE <= 0) Then
		GUICtrlSetData($LABEL_DAMAGE, "Damage During Attack: " & ($POTENTIAL_DAMAGE * -1) & " (LIFE UP)")
	Else
		GUICtrlSetData($LABEL_DAMAGE, "Damage During Attack: " & ($POTENTIAL_DAMAGE * -1) & " (LIFE DOWN)")
	EndIf
	
	If IniRead($SOFTWARE_CONFIG, $CFG_FLYESCAPE_ROOT_KEY, $CFG_FLYESCAPE_FLAG_KEY, 0) = 1 Then
		
		If $POTENTIAL_DAMAGE > Int(IniRead($SOFTWARE_CONFIG, $CFG_FLYESCAPE_ROOT_KEY, $CFG_FLYESCAPE_DAMAGE_KEY, $MAXHP)) Then

			GUICtrlSetData($LABEL_BOT_STATUS, "Status: Flying To Escape")

			ControlSend($APP_TITLE, "", "", IniRead($SOFTWARE_CONFIG, $CFG_FLYESCAPE_ROOT_KEY, $CFG_FLYESCAPE_KEY, ""))
			
			Local $I = 0
			For $I = 0 To IniRead($SOFTWARE_CONFIG, $CFG_FLYESCAPE_ROOT_KEY, $CFG_FLYESCAPE_SPACE_KEY, 10)
				ControlFocus($APP_TITLE, "", "")
				Sleep(300)
				Send("{SPACE DOWN}")
				Sleep(1000)
				Send("{SPACE UP}")
			Next
			
			Sleep(30000)
			GUICtrlSetData($LABEL_BOT_STATUS, "Status: Going Back")
			ControlSend($APP_TITLE, "", "", IniRead($SOFTWARE_CONFIG, $CFG_FLYESCAPE_ROOT_KEY, $CFG_FLYESCAPE_KEY, ""))
		EndIf
		
	EndIf
	
EndFunc

Func HPMPRestoreCheck()

	If IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOREST_HP_KEY, 0) = 1 Then	
		If Int ($HP_PERC) < Int(IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOREST_HP_PERC_KEY, 1)) Then
			RestoreCharState(1)
		Endif
	EndIf	

	If IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOREST_MP_KEY, 0) = 1 Then	
		If Int ($MP_PERC) < Int(IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOREST_MP_PERC_KEY, 1)) Then
			RestoreCharState(2)
		Endif
	EndIf	

EndFunc

Func RestoreCharState($MODE)

	GUICtrlSetData($LABEL_GENERAL_STATUS, "Action: Restoring")
	
	$KEY = IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOREST_KEY, "")
	ControlSend($APP_TITLE, "", "", $KEY)
	Sleep(300)

	$RestTimer = TimerInit()

	If $MODE = 1 Then
		Do 
			Sleep(500)
			UpdateCharInfo()
			$RestTimerDiff = TimerDiff($RestTimer)
			$TARGET_STATE = _MemoryPointerRead($APP_BASE_ADDRESS, $PROCESS_INFORMATION, $OFFSET_AT)
		Until $RestTimerDiff / 60000 > 3 Or $HP_PERC > 98 Or $TARGET_STATE[1] <> 0
	Elseif $MODE = 2 Then
		Do 
			Sleep(500)
			UpdateCharInfo()
			$RestTimerDiff = TimerDiff($RestTimer)
			$TARGET_STATE = _MemoryPointerRead($APP_BASE_ADDRESS, $PROCESS_INFORMATION, $OFFSET_AT)
		Until $RestTimerDiff / 60000 > 3 Or $MP_PERC > 98 Or $TARGET_STATE[1] <> 0
	EndIf
		
	ControlSend($APP_TITLE, "", "", $KEY)
	Sleep(300)

EndFunc


;********************************************************************************
;* Main Form Methods & Events                                                   *
;********************************************************************************
Func SetSkills()
	
	Global $FORM_SKILL = GUICreate("Skills", 255, 400, -1, -1, -1, -1, $FORM_MAIN)
	GUISetOnEvent($GUI_EVENT_CLOSE, "WindowSkillCloseClicked")
	GUISwitch($FORM_SKILL)

	Global $SCOMBOKEY[9], $SKCOUNT, $LABELSK1[9], $LABELSK2[9], $LABELSK3[9], $SDELAY[9], $FORM_SKILL
	$SKCOUNTCFG = IniRead($SOFTWARE_CONFIG, $CFG_SKILLS_ROOT_KEY, $CFG_SKILL_UBOUND_KEY, 1)
	
	
	$GROUP_SKILLS = GUICtrlCreateGroup("Skills", 10, 10, 235, 380)
	
	$BUTTON_ADD_SKILL = GUICtrlCreateButton("Add", 75, 28, 50, 18)
	GUICtrlSetOnEvent(-1, "AddSkill")

	$BUTTON_REMOVE_SKILL = GUICtrlCreateButton("Remove", 130, 28, 50, 18)
	GUICtrlSetOnEvent(-1, "RemoveSkill")

	$BUTTON_SAVE_SKILLS = GUICtrlCreateButton("Save", 185, 28, 50, 18)
	GUICtrlSetOnEvent(-1, "SaveSkills")


	For $SKCOUNT = 1 To IniRead($SOFTWARE_CONFIG, $CFG_SKILLS_ROOT_KEY, $CFG_SKILL_UBOUND_KEY, 1) Step +1

		$LABELSK1[$SKCOUNT] = GUICtrlCreateLabel("Key", 20, 57 + ($SKCOUNT - 1) * 42, 30, 20)

		$SCOMBOKEY[$SKCOUNT] = GUICtrlCreateCombo("", 50, 55 + ($SKCOUNT - 1) * 42, 60, 150)
		GUICtrlSetData(-1, "--|{F1}|{F2}|{F3}|{F4}|{F5}|{F6}|{F7}|{F8}|1|2|3|4|5|6|!1|!2", IniRead($SOFTWARE_CONFIG, $CFG_SKILLS_ROOT_KEY, $CFG_SKILL_COMBO_KEY & $SKCOUNT, "{F1}"))

		$LABELSK2[$SKCOUNT] = GUICtrlCreateLabel("Delay", 120, 57 + ($SKCOUNT - 1) * 42, 50, 20)

		$SDELAY[$SKCOUNT] = GUICtrlCreateInput(IniRead($SOFTWARE_CONFIG, $CFG_SKILLS_ROOT_KEY, $CFG_SKILL_DELAY_KEY & $SKCOUNT, "1"), 155, 55 + ($SKCOUNT - 1) * 42, 40, 20)
		
		$LABELSK3[$SKCOUNT] = GUICtrlCreateLabel("secs", 200, 57 + ($SKCOUNT - 1) * 42, 35, 20)
		
	Next
	
	$SKCOUNT = $SKCOUNT - 1

	GUISetState(@SW_SHOW, $FORM_SKILL)
	
EndFunc   ;==>SetSkills

Func SetAutoPotRest()

	Global $FORM_AUTOPOTREST = GUICreate("Auto-Pot/Rest", 476, 250, -1, -1, -1, -1, $FORM_MAIN)
	GUISetOnEvent($GUI_EVENT_CLOSE, "WindowAutoPotRestCloseClicked")
	GUISwitch($FORM_AUTOPOTREST)

	$BUTTON_SAVE_AUTOPOTREST = GUICtrlCreateButton("Save", 389, 225, 80, 18)
	GUICtrlSetOnEvent(-1, "SaveAutoPotRest")

	;Auto Rest Section
	;--------------------------------------------------------------------------------
	
	Global $CHECK_AUTO_REST_HP, $CHECK_AUTO_REST_MP, $SLIDE_AUTO_REST_HP, $SLIDE_AUTO_REST_MP, $REST_KEY
	
	$GROUP_AUTO_REST = GUICtrlCreateGroup("Automatic Pot/Rest", 5, 5, 160, 215)

	;Auto-Rest HP
	$CHECK_AUTO_REST_HP = GUICtrlCreateCheckbox("Restore HP", 20, 25, 90, 20)

	If IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOREST_HP_KEY, "0") = 1 Then
		$STATE = $GUI_CHECKED
	Else
		$STATE = $GUI_UNCHECKED
	EndIf
	
	GUICtrlSetState(-1, $STATE)

	$SLIDE_AUTO_REST_HP = GUICtrlCreateSlider(25, 50, 100, 20)
	GUICtrlSetData(-1, IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOREST_HP_PERC_KEY, "0"))
	GUICtrlSetLimit(-1, 100, 0)
	GUICtrlCreateLabel("0           50        100% HP", 30, 80, 120, 20)
	;End Auto-Rest HP

	;Auto-Rest MP
	$CHECK_AUTO_REST_MP = GUICtrlCreateCheckbox("Restore MP", 20, 100, 90, 20)

	If IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOREST_MP_KEY, "0") = 1 Then
		GUICtrlSetState(-1, $GUI_CHECKED)
	Else
		GUICtrlSetState(-1, $GUI_UNCHECKED)
	EndIf

	$SLIDE_AUTO_REST_MP = GUICtrlCreateSlider(25, 130, 100, 20)
	GUICtrlSetData(-1, IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOREST_MP_PERC_KEY, "0"))
	GUICtrlSetLimit(-1, 100, 0)
	GUICtrlCreateLabel("0           50        100% MP", 30, 160, 120, 20)
	;End Auto-Rest MP

	;Auto-Rest Key
	GUICtrlCreateLabel("Key:", 20, 180, 50, 20)
	$REST_KEY = GUICtrlCreateCombo(IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOREST_KEY, "--"), 60, 180, 50, 20)
	GUICtrlSetData(-1, "--|{F1}|{F2}|{F3}|{F4}|{F5}|{F6}|{F7}|{F8}|1|2|3|4|5|6", "")
	;End Auto-Rest Key
	
	;--------------------------------------------------------------------------------
	;End Auto Rest Section


	;Auto Loot Section
	;--------------------------------------------------------------------------------
	Global $CHECK_AUTO_PICK, $AUTO_PICK_TIMES, $AUTO_PICK_KEY

	$GROUP_AUTO_PICK = GUICtrlCreateGroup("Automatic Get Loot", 170, 5, 300, 50)

	$CHECK_AUTO_PICK = GUICtrlCreateCheckbox("Auto Looting", 185, 25, 80, 20)

	If IniRead($SOFTWARE_CONFIG, $CFG_LOOT_ROOT_KEY, $CFG_LOOT_FLAG_KEY, "0") = 1 Then
		GUICtrlSetState(-1, $GUI_CHECKED)
	Else
		GUICtrlSetState(-1, $GUI_UNCHECKED)
	EndIf

	GUICtrlCreateLabel("Key:", 275, 28, 40, 20)

	$AUTO_PICK_KEY = GUICtrlCreateCombo("", 305, 25, 50, 50)
	GUICtrlSetData(-1, "--|{F1}|{F2}|{F3}|{F4}|{F5}|{F6}|{F7}|{F8}|1|2|3|4|5|6", IniRead($SOFTWARE_CONFIG, $CFG_LOOT_ROOT_KEY, $CFG_LOOT_KEY, "--"))

	GUICtrlCreateLabel("Times:", 360, 28, 40, 20)

	$AUTO_PICK_TIMES = GUICtrlCreateInput("", 400, 25, 50, 18)
	GUICtrlSetData(-1, IniRead($SOFTWARE_CONFIG, $CFG_LOOT_ROOT_KEY, $CFG_LOOT_TIMES_KEY, "1"))
	;--------------------------------------------------------------------------------
	;End Auto Loot Section
	
	;Auto Pot Section
	;--------------------------------------------------------------------------------
	
	Global $CHECK_AUTOPOT_FLAG_HP, $CHECK_AUTOPOT_FLAG_MP, $SLIDE_AUTOPOT_HP, $SLIDE_AUTOPOT_MP, $AUTOPOT_HP_PERC, $AUTOPOT_MP_PERC
	Global $AUTOPOT_HP_KEY, $AUTOPOT_MP_KEY
	Global $CHECK_STOP_ON_DIE
	
	$GROUP_AUTOPOT = GUICtrlCreateGroup("Automatic Pot", 170, 60, 300, 160)
	
	;Auto-Pot HP
	$CHECK_AUTOPOT_FLAG_HP = GUICtrlCreateCheckbox("Auto-Pot HP", 185, 85, 90, 20)

	If IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOPOT_HP_FLAG_KEY, "0") = 1 Then
		$STATE = $GUI_CHECKED
	Else
		$STATE = $GUI_UNCHECKED
	EndIf
	
	GUICtrlSetState(-1, $STATE)

	$SLIDE_AUTOPOT_HP = GUICtrlCreateSlider(190, 110, 100, 20)
	GUICtrlSetData(-1, IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOPOT_HP_PERC_KEY, "0"))
	GUICtrlSetLimit(-1, 100, 0)
	GUICtrlCreateLabel("0           50        100% HP", 195, 130, 120, 20)
	
	GUICtrlCreateLabel("Key:", 185, 157, 50, 20)
	$AUTOPOT_HP_KEY = GUICtrlCreateCombo(IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOPOT_HP_KEY, "--"), 215, 155, 50, 20)
	GUICtrlSetData(-1, "--|{F1}|{F2}|{F3}|{F4}|{F5}|{F6}|{F7}|{F8}|1|2|3|4|5|6", "")
	;End Auto-Pot HP

	;Auto-Pot MP
	$CHECK_AUTOPOT_FLAG_MP = GUICtrlCreateCheckbox("Auto-Pot MP", 330, 85, 90, 20)

	If IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOPOT_MP_FLAG_KEY, "0") = 1 Then
		GUICtrlSetState(-1, $GUI_CHECKED)
	Else
		GUICtrlSetState(-1, $GUI_UNCHECKED)
	EndIf

	$SLIDE_AUTOPOT_MP = GUICtrlCreateSlider(335, 110, 100, 20)
	GUICtrlSetData(-1, IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOPOT_MP_PERC_KEY, "0"))
	GUICtrlSetLimit(-1, 100, 0)
	GUICtrlCreateLabel("0           50        100% MP", 340, 130, 120, 20)

	GUICtrlCreateLabel("Key:", 330, 157, 50, 20)
	$AUTOPOT_MP_KEY = GUICtrlCreateCombo(IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOPOT_MP_KEY, "--"), 360, 155, 50, 20)
	GUICtrlSetData(-1, "--|{F1}|{F2}|{F3}|{F4}|{F5}|{F6}|{F7}|{F8}|1|2|3|4|5|6", "")
	;End Auto-Pot MP

	;Stop On Die
	$CHECK_STOP_ON_DIE = GUICtrlCreateCheckbox("Stop On Die", 185, 190, 90, 20)

	If IniRead($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_STOP_ON_DIE_KEY, "0") = 1 Then
		GUICtrlSetState(-1, $GUI_CHECKED)
	Else
		GUICtrlSetState(-1, $GUI_UNCHECKED)
	EndIf

	;End Stop On Die


	;--------------------------------------------------------------------------------
	;End Auto Pot Section

	
	GUISetState(@SW_SHOW, $FORM_AUTOPOTREST)

EndFunc

Func SetAutoBuffs()

	
	Global $FORM_BUFFS = GUICreate("Auto-Buff", 370, 230, -1, -1, -1, -1, $FORM_MAIN)
	GUISetOnEvent($GUI_EVENT_CLOSE, "WindowAutoBuffCloseClicked")
	GUISwitch($FORM_BUFFS)

	Global $BUFFSCOMBOKEY[9], $BUFFSCOUNT, $LABELBUFFS1[9], $LABELBUFFS2[9], $LABELBUFFS3[9], $LABELBUFFS4[9], $LABELBUFFS5[9], $BUFFSDELAY[9], $BUFFSFREQUENCY[9]
	Global $BUFFS_FLAG
	$SKCOUNTCFG_BUFFS = IniRead($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_UBOUND_KEY, 1)
	
	$GROUP_BUFFS = GUICtrlCreateGroup("Buffs", 10, 10, 355, 210)
	
	$BUTTON_ADD_BUFFS = GUICtrlCreateButton("Add", 75, 28, 50, 18)
	GUICtrlSetOnEvent(-1, "AddBuffs")

	$BUTTON_REMOVE_BUFFS = GUICtrlCreateButton("Remove", 130, 28, 50, 18)
	GUICtrlSetOnEvent(-1, "RemoveBuffs")

	$BUTTON_SAVE_BUFFS = GUICtrlCreateButton("Save", 185, 28, 50, 18)
	GUICtrlSetOnEvent(-1, "SaveBuffs")
	
	$BUFFS_FLAG = GUICtrlCreateCheckbox("Use Auto-Buffs", 250, 28, 100, 18)
	
	If IniRead($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_FLAG_KEY, "0") = 1 Then
		GUICtrlSetState(-1, $GUI_CHECKED)
	Else
		GUICtrlSetState(-1, $GUI_UNCHECKED) 
	EndIf	

	For $BUFFSCOUNT = 1 To IniRead($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_UBOUND_KEY, 1) Step +1

		$LABELBUFFS1[$BUFFSCOUNT] = GUICtrlCreateLabel("Key", 20, 57 + ($BUFFSCOUNT - 1) * 42, 30, 20)

		$BUFFSCOMBOKEY[$BUFFSCOUNT] = GUICtrlCreateCombo("", 50, 55 + ($BUFFSCOUNT - 1) * 42, 60, 150)
		GUICtrlSetData(-1, "--|{F1}|{F2}|{F3}|{F4}|{F5}|{F6}|{F7}|{F8}|1|2|3|4|5|6|!1|!2", IniRead($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_COMBO_KEY & $BUFFSCOUNT, "{F1}"))

		$LABELBUFFS2[$BUFFSCOUNT] = GUICtrlCreateLabel("Delay", 120, 57 + ($BUFFSCOUNT - 1) * 42, 50, 20)

		$BUFFSDELAY[$BUFFSCOUNT] = GUICtrlCreateInput(IniRead($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_DELAY_KEY & $BUFFSCOUNT, "1"), 155, 55 + ($BUFFSCOUNT - 1) * 42, 40, 20)
		
		$LABELBUFFS3[$BUFFSCOUNT] = GUICtrlCreateLabel("secs", 200, 57 + ($BUFFSCOUNT - 1) * 42, 35, 20)

		$LABELBUFFS4[$BUFFSCOUNT] = GUICtrlCreateLabel("Interval", 235, 57 + ($BUFFSCOUNT - 1) * 42, 50, 20)

		$BUFFSFREQUENCY[$BUFFSCOUNT] = GUICtrlCreateInput(IniRead($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_FREQUENCY_KEY & $BUFFSCOUNT, "1"), 280, 55 + ($BUFFSCOUNT - 1) * 42, 40, 20)
		
		$LABELBUFFS5[$BUFFSCOUNT] = GUICtrlCreateLabel("mins", 325, 57 + ($BUFFSCOUNT - 1) * 42, 35, 20)

	Next
	
	$BUFFSCOUNT = $BUFFSCOUNT - 1

	GUISetState(@SW_SHOW, $FORM_BUFFS)

EndFunc

Func SetChangeWeapons()

	
	Global $FORM_WEAPONS = GUICreate("Auto Change Weapons", 340, 230, -1, -1, -1, -1, $FORM_MAIN)
	GUISetOnEvent($GUI_EVENT_CLOSE, "WindowChangeWeaponsCloseClicked")
	GUISwitch($FORM_WEAPONS)

	Global $WEAPONSCOMBOKEY[9], $WEAPONSCOUNT, $LABELWEAPONS1[9], $WEAPONS_DELAY
	Global $WEAPONS_FLAG
	$SKCOUNTCFG_WEAPONS = IniRead($SOFTWARE_CONFIG, $CFG_WEAPONS_ROOT_KEY, $CFG_WEAPONS_UBOUND_KEY, 1)
	
	$GROUP_WEAPONS = GUICtrlCreateGroup("Change Weapons", 10, 10, 185, 210)
	
	$BUTTON_ADD_WEAPONS = GUICtrlCreateButton("Add", 25, 28, 50, 18)
	GUICtrlSetOnEvent(-1, "AddWeapon")

	$BUTTON_REMOVE_WEAPONS = GUICtrlCreateButton("Remove", 80, 28, 50, 18)
	GUICtrlSetOnEvent(-1, "RemoveWeapon")

	$BUTTON_SAVE_WEAPONS = GUICtrlCreateButton("Save", 135, 28, 50, 18)
	GUICtrlSetOnEvent(-1, "SaveWeapons")
	
	$WEAPONS_FLAG = GUICtrlCreateCheckbox("Use Change-Weapons", 200, 28, 130, 18)

	If IniRead($SOFTWARE_CONFIG, $CFG_WEAPONS_ROOT_KEY, $CFG_WEAPONS_FLAG_KEY, "0") = 1 Then
		GUICtrlSetState(-1, $GUI_CHECKED)
	Else
		GUICtrlSetState(-1, $GUI_UNCHECKED) 
	EndIf	
	
	$LABEL_WEAPONS_DELAY = GUICtrlCreateLabel("Interval (In Minutes)", 200, 55, 130, 18)
	$WEAPONS_DELAY = GUICtrlCreateInput(IniRead($SOFTWARE_CONFIG, $CFG_WEAPONS_ROOT_KEY, $CFG_WEAPONS_DELAY_KEY, "180"), 200, 75, 50, 20)

	For $WEAPONSCOUNT = 1 To IniRead($SOFTWARE_CONFIG, $CFG_WEAPONS_ROOT_KEY, $CFG_WEAPONS_UBOUND_KEY, 1) Step +1

		$LABELWEAPONS1[$WEAPONSCOUNT] = GUICtrlCreateLabel("Weapon " & $WEAPONSCOUNT, 20, 57 + ($WEAPONSCOUNT - 1) * 42, 50, 20)

		$WEAPONSCOMBOKEY[$WEAPONSCOUNT] = GUICtrlCreateCombo("", 90, 55 + ($WEAPONSCOUNT - 1) * 42, 60, 150)
		GUICtrlSetData(-1, "--|{F1}|{F2}|{F3}|{F4}|{F5}|{F6}|{F7}|{F8}|1|2|3|4|5|6|!1|!2", IniRead($SOFTWARE_CONFIG, $CFG_WEAPONS_ROOT_KEY, $CFG_WEAPONS_COMBO_KEY & $WEAPONSCOUNT, "{F1}"))

	Next
	
	$WEAPONSCOUNT = $WEAPONSCOUNT - 1

	GUISetState(@SW_SHOW, $FORM_WEAPONS)

EndFunc

Func SetFlyEscape()

	Global $FORM_FLYESCAPE = GUICreate("Fly Escape", 255, 170, -1, -1, -1, -1, $FORM_MAIN)
	GUISetOnEvent($GUI_EVENT_CLOSE, "WindowFlyEscapeCloseClicked")
	GUISwitch($FORM_FLYESCAPE)
	
	Global $CHECK_FLYESCAPE, $FLYESCAPE_KEY, $FLYESCAPE_DAMAGE_KEY, $FLY_ESCAPE_SPACE_KEY, $BUTTON_FLY_ESCAPE_SAVE
	
	$GROUP_FLYESCAPE = GUICtrlCreateGroup(" Fly Escape Options ", 5, 5, 250, 130)
	
	$CHECK_FLYESCAPE = GUICtrlCreateCheckbox("Try to fly to escape death", 15, 25, 200, 20)

	If IniRead($SOFTWARE_CONFIG, $CFG_FLYESCAPE_ROOT_KEY, $CFG_FLYESCAPE_FLAG_KEY, "0") = 1 Then
		GUICtrlSetState(-1, $GUI_CHECKED)
	Else
		GUICtrlSetState(-1, $GUI_UNCHECKED)
	EndIf
	
	GUICtrlCreateLabel("Key to Fly:", 15, 55, 50, 20)
	$FLYESCAPE_KEY = GUICtrlCreateCombo(IniRead($SOFTWARE_CONFIG, $CFG_FLYESCAPE_ROOT_KEY, $CFG_FLYESCAPE_KEY, "--"), 125, 50, 50, 20)
	GUICtrlSetData(-1, "--|{F1}|{F2}|{F3}|{F4}|{F5}|{F6}|{F7}|{F8}|1|2|3|4|5|6", "")
	
	GUICtrlCreateLabel("Check Damage >= ", 15, 85, 100, 20)
	$FLYESCAPE_DAMAGE_KEY = GUICtrlCreateInput(IniRead($SOFTWARE_CONFIG, $CFG_FLYESCAPE_ROOT_KEY, $CFG_FLYESCAPE_DAMAGE_KEY, "0"), 125, 80, 50, 20)


	GUICtrlCreateLabel("Total Spaces Hits", 15, 110, 110, 20)
	$FLY_ESCAPE_SPACE_KEY = GUICtrlCreateInput(IniRead($SOFTWARE_CONFIG, $CFG_FLYESCAPE_ROOT_KEY, $CFG_FLYESCAPE_SPACE_KEY, "0"), 125,105, 50, 20)


	$BUTTON_FLY_ESCAPE_SAVE = GUICtrlCreateButton("Save", 170, 140, 80, 20)
	GUICtrlSetOnEvent(-1, "SaveFlyToEscape")

	GUISetState(@SW_SHOW, $FORM_FLYESCAPE)
	
EndFunc


Func StartOrStop()
	
	If $STOP = True Then
		AdlibDisable()
		GUICtrlSetData($BUTTON_START, "Stop")
		GUICtrlSetOnEvent($BUTTON_START, "StartOrStop")
		$STOP = False
	Else
		AdlibEnable("Main")
		GUICtrlSetData($BUTTON_START, "Start")
		GUICtrlSetOnEvent($BUTTON_START, "StartOrStop")
		$STOP = True
	EndIf
	
EndFunc

Func ResetMobList()
	
	$COUNT = 0
	
	Do
		$COUNT = $COUNT + 1
		IniWrite($SOFTWARE_CONFIG, $CFG_MOBLIST_ROOT_KEY, $CFG_MOBLIST_MONSTER_KEY & $COUNT, "")
	Until $COUNT > IniRead($SOFTWARE_CONFIG, $CFG_MOBLIST_ROOT_KEY, $CFG_MOBLIST_UBOUND_KEY, 1)
	
	IniWrite($SOFTWARE_CONFIG, $CFG_MOBLIST_ROOT_KEY, $CFG_MOBLIST_UBOUND_KEY, "0")

	RefreshMobList()

	MsgBox(0, "MobList Reseted", "The MobList Was Reseted!")
	
EndFunc   ;==>ResetMobList

Func RefreshMobList()

	GUICtrlDelete($LIST_MOB)
	GUICtrlCreateListView("Priority|Mob ID                            ", 5, 95, 250, 100)
	$COUNT = 0
	
	Do
		$COUNT = $COUNT + 1
		GUICtrlCreateListViewItem($COUNT & ")|" & IniRead($SOFTWARE_CONFIG, $CFG_MOBLIST_ROOT_KEY, $CFG_MOBLIST_MONSTER_KEY & $COUNT, ""), $LIST_MOB)
	Until $COUNT >= IniRead($SOFTWARE_CONFIG, $CFG_MOBLIST_ROOT_KEY, $CFG_MOBLIST_UBOUND_KEY, 1)
	
EndFunc   ;==>RefreshMobList

Func SetMobList()
	
	$MOB_LIST_COUNT = 0
	ToolTip("Now go back to the game!", 0, 0)
	WinWaitActive($APP_TITLE)
	ToolTip("Select the monster that you want to fight then press F11. You can do that 10 times.", 0, 0)
	HotKeySet("{F11}", "SaveMobInMobList")
	
EndFunc   ;==>SetMobList

Func SaveMobInMobList()
	
	HotKeySet("{F10}", "EndSelectMobList")
	
	$MOB_LIST_COUNT = $MOB_LIST_COUNT + 1

	If $MOB_LIST_COUNT <= 10 Then
		
		$TARGET_STATE = _MemoryPointerRead($APP_BASE_ADDRESS, $PROCESS_INFORMATION, $OFFSET_AT)

		If Not @error Then
			
			$TARGET_ID = Hex($TARGET_STATE[1])

			IniWrite($SOFTWARE_CONFIG, $CFG_MOBLIST_ROOT_KEY, $CFG_MOBLIST_MONSTER_KEY & $MOB_LIST_COUNT, $TARGET_ID)
			
			If $MOB_LIST_COUNT <= 9 Then
				ToolTip("Monster " & $MOB_LIST_COUNT & " Saved" & @CRLF & "Select another monsters and press F11 to set or F10 To end", 0, 0)
				IniWrite($SOFTWARE_CONFIG, $CFG_MOBLIST_ROOT_KEY, $CFG_MOBLIST_UBOUND_KEY, $MOB_LIST_COUNT)
			Else
				ToolTip("Monster " & $MOB_LIST_COUNT & " Saved" & @CRLF & "Now Press Start/F9 to start Bot", 0, 0)
				IniWrite($SOFTWARE_CONFIG, $CFG_MOBLIST_ROOT_KEY, $CFG_MOBLIST_UBOUND_KEY, $MOB_LIST_COUNT)
			EndIf

			Sleep(1000)
			ToolTip("")
		Else
			ToolTip("Error!" & @error)
		EndIf
		
	Else
		ToolTip("Max Monsters Reached. Now Press Start/F9 to start Bot", 0, 0)
	EndIf
	
	RefreshMobList()
	
EndFunc   ;==>SaveMobInMobList

Func EndSelectMobList()
	
	HotKeySet("{F11}")
	ToolTip("Set MobList Finished, Now Press F9 to Start", 0, 0)
	Sleep(1000)
	ToolTip("")
	HotKeySet("{F10}")
	
EndFunc   ;==>EndSelectMobList

;********************************************************************************
;* Skills Form Methods & Events                                                 *
;********************************************************************************

Func AddSkill()
	
	GUISwitch($FORM_SKILL)
	
	$SKCOUNTCFG = $SKCOUNTCFG + 1

	If $SKCOUNTCFG >= 8 Then
		
		$SKCOUNTCFG = 8
		MsgBox(0, "Error", "Max Skills Reached")

	Else

		$LABELSK1[$SKCOUNTCFG] = GUICtrlCreateLabel("Key", 20, 57 + ($SKCOUNTCFG - 1) * 42, 30, 20)

		$SCOMBOKEY[$SKCOUNTCFG] = GUICtrlCreateCombo("", 50, 55 + ($SKCOUNTCFG - 1) * 42, 60, 150)
		GUICtrlSetData(-1, "--|{F1}|{F2}|{F3}|{F4}|{F5}|{F6}|{F7}|{F8}|1|2|3|4|5|6|!1|!2", IniRead($SOFTWARE_CONFIG, $CFG_SKILLS_ROOT_KEY, $CFG_SKILL_COMBO_KEY & $SKCOUNTCFG, "{F1}"))

		$LABELSK2[$SKCOUNTCFG] = GUICtrlCreateLabel("Delay", 120, 57 + ($SKCOUNTCFG - 1) * 42, 50, 20)

		$SDELAY[$SKCOUNTCFG] = GUICtrlCreateInput(IniRead($SOFTWARE_CONFIG, $CFG_SKILLS_ROOT_KEY, $CFG_SKILL_DELAY_KEY & $SKCOUNTCFG, "1"), 155, 55 + ($SKCOUNTCFG - 1) * 42, 40, 20)
		
		$LABELSK3[$SKCOUNTCFG] = GUICtrlCreateLabel("secs", 200, 57 + ($SKCOUNTCFG - 1) * 42, 35, 20)
		
	EndIf
	
EndFunc   ;==>AddSkill


Func RemoveSkill()
	
	GUISwitch($FORM_SKILL)
	
	If $SKCOUNTCFG < 2 Then
		$SKCOUNTCFG = 1
		MsgBox(0, "Error", "Minimum Skills Reached")
	Else
		GUICtrlDelete($SCOMBOKEY[$SKCOUNTCFG])
		GUICtrlDelete($LABELSK1[$SKCOUNTCFG])
		GUICtrlDelete($LABELSK2[$SKCOUNTCFG])
		GUICtrlDelete($LABELSK3[$SKCOUNTCFG])
		GUICtrlDelete($SDELAY[$SKCOUNTCFG])
		$SKCOUNTCFG = $SKCOUNTCFG - 1
	EndIf
	
EndFunc   ;==>RemoveSkill

Func SaveSkills()
	
	$COUNT = 1
	$MAX = $SKCOUNTCFG
	$ACTIVE_SKILL = 1
	
	IniWrite($SOFTWARE_CONFIG, $CFG_SKILLS_ROOT_KEY, $CFG_SKILL_UBOUND_KEY, $MAX)
	IniWrite($SOFTWARE_CONFIG, $CFG_SKILLS_ROOT_KEY, $CFG_SKILL_COMBO_KEY & $COUNT, GUICtrlRead($SCOMBOKEY[$COUNT]))
	IniWrite($SOFTWARE_CONFIG, $CFG_SKILLS_ROOT_KEY, $CFG_SKILL_DELAY_KEY & $COUNT, GUICtrlRead($SDELAY[$COUNT]))

	Do
		$COUNT = $COUNT + 1
		IniWrite($SOFTWARE_CONFIG, $CFG_SKILLS_ROOT_KEY, $CFG_SKILL_COMBO_KEY & $COUNT, GUICtrlRead($SCOMBOKEY[$COUNT]))
		IniWrite($SOFTWARE_CONFIG, $CFG_SKILLS_ROOT_KEY, $CFG_SKILL_DELAY_KEY & $COUNT, GUICtrlRead($SDELAY[$COUNT]))
	Until $COUNT >= $MAX
	
	MsgBox(0, "Skills Saved", "The settings for skills are saved!")
	GUIDelete($FORM_SKILL)
	
EndFunc   ;==>SaveSkills

;********************************************************************************
;* Auto-Pot Rest Form Methods & Events                                                 *
;********************************************************************************

Func SaveAutoPotRest()

	IniWrite($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOREST_HP_KEY, GUICtrlRead($CHECK_AUTO_REST_HP))
	IniWrite($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOREST_HP_PERC_KEY, GUICtrlRead($SLIDE_AUTO_REST_HP))
	IniWrite($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOREST_MP_KEY, GUICtrlRead($CHECK_AUTO_REST_MP))
	IniWrite($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOREST_MP_PERC_KEY, GUICtrlRead($SLIDE_AUTO_REST_MP))
	IniWrite($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOREST_KEY, GUICtrlRead($REST_KEY))

	IniWrite($SOFTWARE_CONFIG, $CFG_LOOT_ROOT_KEY, $CFG_LOOT_FLAG_KEY, GUICtrlRead($CHECK_AUTO_PICK))
	IniWrite($SOFTWARE_CONFIG, $CFG_LOOT_ROOT_KEY, $CFG_LOOT_KEY, GUICtrlRead($AUTO_PICK_KEY))
	IniWrite($SOFTWARE_CONFIG, $CFG_LOOT_ROOT_KEY, $CFG_LOOT_TIMES_KEY, GUICtrlRead($AUTO_PICK_TIMES))

	IniWrite($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOPOT_HP_FLAG_KEY, GUICtrlRead($CHECK_AUTOPOT_FLAG_HP))
	IniWrite($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOPOT_HP_PERC_KEY, GUICtrlRead($SLIDE_AUTOPOT_HP))
	IniWrite($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOPOT_MP_FLAG_KEY, GUICtrlRead($CHECK_AUTOPOT_FLAG_MP))
	IniWrite($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOPOT_MP_PERC_KEY, GUICtrlRead($SLIDE_AUTOPOT_MP))
	IniWrite($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOPOT_HP_KEY, GUICtrlRead($AUTOPOT_HP_KEY))
	IniWrite($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_AUTOPOT_MP_KEY, GUICtrlRead($AUTOPOT_MP_KEY))

	IniWrite($SOFTWARE_CONFIG, $CFG_HEAL_ROOT_KEY, $CFG_HEAL_STOP_ON_DIE_KEY, GUICtrlRead($CHECK_STOP_ON_DIE))
	
	MsgBox(0, "Auto-Pot/Rest Saved", "The settings for auto-pot/rest are saved!")
	GUIDelete($FORM_AUTOPOTREST)

EndFunc   ;==>SaveAutoPotRest

;********************************************************************************
;* Auto-Pot Rest Form Methods & Events                                                 *
;********************************************************************************

Func SaveFlyToEscape()

	IniWrite($SOFTWARE_CONFIG, $CFG_FLYESCAPE_ROOT_KEY, $CFG_FLYESCAPE_FLAG_KEY, GUICtrlRead($CHECK_FLYESCAPE))
	IniWrite($SOFTWARE_CONFIG, $CFG_FLYESCAPE_ROOT_KEY, $CFG_FLYESCAPE_KEY, GUICtrlRead($FLYESCAPE_KEY))
	IniWrite($SOFTWARE_CONFIG, $CFG_FLYESCAPE_ROOT_KEY, $CFG_FLYESCAPE_DAMAGE_KEY, GUICtrlRead($FLYESCAPE_DAMAGE_KEY))
	IniWrite($SOFTWARE_CONFIG, $CFG_FLYESCAPE_ROOT_KEY, $CFG_FLYESCAPE_SPACE_KEY, GUICtrlRead($FLY_ESCAPE_SPACE_KEY))

	MsgBox(0, "FlyToEscape Settings Saved", "The settings for fly to escape are saved!")
	GUIDelete($FORM_FLYESCAPE)

EndFunc  

;********************************************************************************
;* Auto-Pot Rest Form Methods & Events                                                 *
;********************************************************************************

Func AddBuffs()

	GUISwitch($FORM_BUFFS)
	
	$SKCOUNTCFG_BUFFS = $SKCOUNTCFG_BUFFS + 1

	If $SKCOUNTCFG_BUFFS >= 5 Then
		
		$SKCOUNTCFG_BUFFS = 4
		MsgBox(0, "Error", "Max Auto-Buffs Reached")

	Else

		$LABELBUFFS1[$SKCOUNTCFG_BUFFS] = GUICtrlCreateLabel("Key", 20, 57 + ($SKCOUNTCFG_BUFFS - 1) * 42, 30, 20)

		$BUFFSCOMBOKEY[$SKCOUNTCFG_BUFFS] = GUICtrlCreateCombo("", 50, 55 + ($SKCOUNTCFG_BUFFS - 1) * 42, 60, 150)
		GUICtrlSetData(-1, "--|{F1}|{F2}|{F3}|{F4}|{F5}|{F6}|{F7}|{F8}|1|2|3|4|5|6|!1|!2", IniRead($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_COMBO_KEY & $SKCOUNTCFG_BUFFS, "{F1}"))

		$LABELBUFFS2[$SKCOUNTCFG_BUFFS] = GUICtrlCreateLabel("Delay", 120, 57 + ($SKCOUNTCFG_BUFFS - 1) * 42, 50, 20)

		$BUFFSDELAY[$SKCOUNTCFG_BUFFS] = GUICtrlCreateInput(IniRead($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_DELAY_KEY & $SKCOUNTCFG_BUFFS, "1"), 155, 55 + ($SKCOUNTCFG_BUFFS - 1) * 42, 40, 20)
		
		$LABELBUFFS3[$SKCOUNTCFG_BUFFS] = GUICtrlCreateLabel("secs", 200, 57 + ($SKCOUNTCFG_BUFFS - 1) * 42, 35, 20)

		$LABELBUFFS4[$SKCOUNTCFG_BUFFS] = GUICtrlCreateLabel("Interval", 235, 57 + ($SKCOUNTCFG_BUFFS - 1) * 42, 50, 20)

		$BUFFSFREQUENCY[$SKCOUNTCFG_BUFFS] = GUICtrlCreateInput(IniRead($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_FREQUENCY_KEY & $BUFFSCOUNT, "1"), 280, 55 + ($SKCOUNTCFG_BUFFS - 1) * 42, 40, 20)
		
		$LABELBUFFS5[$SKCOUNTCFG_BUFFS] = GUICtrlCreateLabel("mins", 325, 57 + ($SKCOUNTCFG_BUFFS - 1) * 42, 35, 20)


	EndIf

EndFunc

Func RemoveBuffs()
	
	GUISwitch($FORM_BUFFS)
	
	If $SKCOUNTCFG_BUFFS < 2 Then
		$SKCOUNTCFG_BUFFS = 1
		MsgBox(0, "Error", "Minimum Auto-Buffs Reached")
	Else
		GUICtrlDelete($BUFFSCOMBOKEY[$SKCOUNTCFG_BUFFS])
		GUICtrlDelete($LABELBUFFS1[$SKCOUNTCFG_BUFFS])
		GUICtrlDelete($LABELBUFFS2[$SKCOUNTCFG_BUFFS])
		GUICtrlDelete($LABELBUFFS3[$SKCOUNTCFG_BUFFS])
		GUICtrlDelete($LABELBUFFS4[$SKCOUNTCFG_BUFFS])
		GUICtrlDelete($LABELBUFFS5[$SKCOUNTCFG_BUFFS])
		GUICtrlDelete($BUFFSDELAY[$SKCOUNTCFG_BUFFS])
		GUICtrlDelete($BUFFSFREQUENCY[$SKCOUNTCFG_BUFFS])
		$SKCOUNTCFG_BUFFS = $SKCOUNTCFG_BUFFS - 1
	EndIf
	
EndFunc

Func SaveBuffs()
	
	$COUNT = 1
	$MAX = $SKCOUNTCFG_BUFFS
	$ACTIVE_BUFFS = 1
	
	IniWrite($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_UBOUND_KEY, $MAX)
	IniWrite($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_FLAG_KEY, GUICtrlRead($BUFFS_FLAG))
	IniWrite($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_COMBO_KEY & $COUNT, GUICtrlRead($BUFFSCOMBOKEY[$COUNT]))
	IniWrite($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_DELAY_KEY & $COUNT, GUICtrlRead($BUFFSDELAY[$COUNT]))
	IniWrite($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_FREQUENCY_KEY & $COUNT, GUICtrlRead($BUFFSFREQUENCY[$COUNT]))

	Do
		$COUNT = $COUNT + 1
		IniWrite($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_COMBO_KEY & $COUNT, GUICtrlRead($BUFFSCOMBOKEY[$COUNT]))
		IniWrite($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_DELAY_KEY & $COUNT, GUICtrlRead($BUFFSDELAY[$COUNT]))
		IniWrite($SOFTWARE_CONFIG, $CFG_BUFFS_ROOT_KEY, $CFG_BUFFS_FREQUENCY_KEY & $COUNT, GUICtrlRead($BUFFSFREQUENCY[$COUNT]))
	Until $COUNT >= $MAX
	
	MsgBox(0, "Auto-Buffs Saved", "The settings for auto-buffs are saved!")
	GUIDelete($FORM_BUFFS)	
	
EndFunc

;********************************************************************************
;* Change Weapons Form Methods & Events                                         *
;********************************************************************************

Func AddWeapon()

	GUISwitch($FORM_WEAPONS)
	
	$SKCOUNTCFG_WEAPONS = $SKCOUNTCFG_WEAPONS + 1

	If $SKCOUNTCFG_WEAPONS >= 5 Then
		
		$SKCOUNTCFG_WEAPONS = 4
		MsgBox(0, "Error", "Max Change Weapons Reached")

	Else

		$LABELWEAPONS1[$SKCOUNTCFG_WEAPONS] = GUICtrlCreateLabel("Weapon " & $SKCOUNTCFG_WEAPONS, 20, 57 + ($SKCOUNTCFG_WEAPONS - 1) * 42, 50, 20)

		$WEAPONSCOMBOKEY[$SKCOUNTCFG_WEAPONS] = GUICtrlCreateCombo("", 90, 55 + ($SKCOUNTCFG_WEAPONS - 1) * 42, 60, 150)
		GUICtrlSetData(-1, "--|{F1}|{F2}|{F3}|{F4}|{F5}|{F6}|{F7}|{F8}|1|2|3|4|5|6|!1|!2", IniRead($SOFTWARE_CONFIG, $CFG_WEAPONS_ROOT_KEY, $CFG_WEAPONS_COMBO_KEY & $SKCOUNTCFG_WEAPONS, "{F1}"))

	EndIf

EndFunc

Func RemoveWeapon()
	
	GUISwitch($FORM_WEAPONS)
	
	If $SKCOUNTCFG_WEAPONS < 2 Then
		$SKCOUNTCFG_WEAPONS = 1
		MsgBox(0, "Error", "Minimum Change Weapons Reached")
	Else
		GUICtrlDelete($WEAPONSCOMBOKEY[$SKCOUNTCFG_WEAPONS])
		GUICtrlDelete($LABELWEAPONS1[$SKCOUNTCFG_WEAPONS])
		$SKCOUNTCFG_WEAPONS = $SKCOUNTCFG_WEAPONS - 1
	EndIf
	
EndFunc

Func SaveWeapons()
	
	$COUNT = 1
	$MAX = $SKCOUNTCFG_WEAPONS
	$ACTIVE_WEAPONS = 0
	
	IniWrite($SOFTWARE_CONFIG, $CFG_WEAPONS_ROOT_KEY, $CFG_WEAPONS_UBOUND_KEY, $MAX)
	IniWrite($SOFTWARE_CONFIG, $CFG_WEAPONS_ROOT_KEY, $CFG_WEAPONS_FLAG_KEY, GUICtrlRead($WEAPONS_FLAG))
	IniWrite($SOFTWARE_CONFIG, $CFG_WEAPONS_ROOT_KEY, $CFG_WEAPONS_DELAY_KEY, GUICtrlRead($WEAPONS_DELAY))
	IniWrite($SOFTWARE_CONFIG, $CFG_WEAPONS_ROOT_KEY, $CFG_WEAPONS_COMBO_KEY & $COUNT, GUICtrlRead($WEAPONSCOMBOKEY[$COUNT]))

	Do
		$COUNT = $COUNT + 1
		IniWrite($SOFTWARE_CONFIG, $CFG_WEAPONS_ROOT_KEY, $CFG_WEAPONS_COMBO_KEY & $COUNT, GUICtrlRead($WEAPONSCOMBOKEY[$COUNT]))
	Until $COUNT >= $MAX
	
	MsgBox(0, "Change Weapons Saved", "The settings for change weapons are saved!")
	GUIDelete($FORM_WEAPONS)	
	
EndFunc

