/*
 * IzPack - Copyright 2001-2005 Julien Ponge, All Rights Reserved.
 * 
 * http://www.izforge.com/izpack/
 * http://izpack.codehaus.org/
 * 
 * Copyright 2002 Elmar Grom
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* Defining AMD64 here because project settings do not keep the preprocessor
 * definition for some reason.
 */
#define AMD64

#include "com_izforge_izpack_util_win_UserInfo.h"
#include "UnicodeHelper.h"
#include <windows.h>
#include <lmcons.h>
#include <Lm.h>
#include <tchar.h>

typedef NTSTATUS (WINAPI *PNESA)(LPWSTR, DWORD, DWORD*, PZPWSTR*);

JNIEXPORT jboolean JNICALL Java_com_izforge_izpack_util_win_UserInfo_isUserAnAdmin(JNIEnv *, jobject)
{
   BOOL   fReturn         = FALSE;
   DWORD  dwStatus;
   DWORD  dwAccessMask;
   DWORD  dwAccessDesired;
   DWORD  dwACLSize;
   DWORD  dwStructureSize = sizeof(PRIVILEGE_SET);
   PACL   pACL            = NULL;
   PSID   psidAdmin       = NULL;

   HANDLE hToken              = NULL;
   HANDLE hImpersonationToken = NULL;

   PRIVILEGE_SET   ps;
   GENERIC_MAPPING GenericMapping;

   PSECURITY_DESCRIPTOR     psdAdmin           = NULL;
   SID_IDENTIFIER_AUTHORITY SystemSidAuthority = SECURITY_NT_AUTHORITY;

   /*
      Determine if the current thread is running as a user that is a member of
      the local admins group.  To do this, create a security descriptor that
      has a DACL which has an ACE that allows only local aministrators access.
      Then, call AccessCheck with the current thread's token and the security
      descriptor.  It will say whether the user could access an object if it
      had that security descriptor.  Note: you do not need to actually create
      the object.  Just checking access against the security descriptor alone
      will be sufficient.
   */
   const DWORD MY_ACCESS_READ  = 1;
   const DWORD MY_ACCESS_WRITE = 2;


   __try
   {

      /*
         AccessCheck() requires an impersonation token.  We first get a primary
         token and then create a duplicate impersonation token.  The
         impersonation token is not actually assigned to the thread, but is
         used in the call to AccessCheck.  Thus, this function itself never
         impersonates, but does use the identity of the thread.  If the thread
         was impersonating already, this function uses that impersonation context.
      */
      if (!OpenThreadToken(GetCurrentThread(), TOKEN_DUPLICATE|TOKEN_QUERY, TRUE, &hToken))
      {
         if (GetLastError() != ERROR_NO_TOKEN)
            __leave;

         if (!OpenProcessToken(GetCurrentProcess(), TOKEN_DUPLICATE|TOKEN_QUERY, &hToken))
            __leave;
      }

      if (!DuplicateToken (hToken, SecurityImpersonation, &hImpersonationToken))
          __leave;


      /*
        Create the binary representation of the well-known SID that
        represents the local administrators group.  Then create the security
        descriptor and DACL with an ACE that allows only local admins access.
        After that, perform the access check.  This will determine whether
        the current user is a local admin.
      */
      if (!AllocateAndInitializeSid(&SystemSidAuthority, 2,
                                    SECURITY_BUILTIN_DOMAIN_RID,
                                    DOMAIN_ALIAS_RID_ADMINS,
                                    0, 0, 0, 0, 0, 0, &psidAdmin))
         __leave;

      psdAdmin = LocalAlloc(LPTR, SECURITY_DESCRIPTOR_MIN_LENGTH);
      if (psdAdmin == NULL)
         __leave;

      if (!InitializeSecurityDescriptor(psdAdmin, SECURITY_DESCRIPTOR_REVISION))
         __leave;

      // Compute size needed for the ACL.
      dwACLSize = sizeof(ACL) + sizeof(ACCESS_ALLOWED_ACE) +
                  GetLengthSid(psidAdmin) - sizeof(DWORD);

      pACL = (PACL)LocalAlloc(LPTR, dwACLSize);
      if (pACL == NULL)
         __leave;

      if (!InitializeAcl(pACL, dwACLSize, ACL_REVISION2))
         __leave;

      dwAccessMask= MY_ACCESS_READ | MY_ACCESS_WRITE;

      if (!AddAccessAllowedAce(pACL, ACL_REVISION2, dwAccessMask, psidAdmin))
         __leave;

      if (!SetSecurityDescriptorDacl(psdAdmin, TRUE, pACL, FALSE))
         __leave;

      /*
         AccessCheck validates a security descriptor somewhat; set the group
         and owner so that enough of the security descriptor is filled out to
         make AccessCheck happy.
      */
      SetSecurityDescriptorGroup(psdAdmin, psidAdmin, FALSE);
      SetSecurityDescriptorOwner(psdAdmin, psidAdmin, FALSE);

      if (!IsValidSecurityDescriptor(psdAdmin))
         __leave;

      dwAccessDesired = MY_ACCESS_READ;

      /*
         Initialize GenericMapping structure even though you
         do not use generic rights.
      */
      GenericMapping.GenericRead    = MY_ACCESS_READ;
      GenericMapping.GenericWrite   = MY_ACCESS_WRITE;
      GenericMapping.GenericExecute = 0;
      GenericMapping.GenericAll     = MY_ACCESS_READ | MY_ACCESS_WRITE;

      if (!AccessCheck(psdAdmin, hImpersonationToken, dwAccessDesired,
                       &GenericMapping, &ps, &dwStructureSize, &dwStatus,
                       &fReturn))
      {
         fReturn = FALSE;
         __leave;
      }
   }
   __finally
   {
      // Clean up.
      if (pACL) LocalFree(pACL);
      if (psdAdmin) LocalFree(psdAdmin);
      if (psidAdmin) FreeSid(psidAdmin);
      if (hImpersonationToken) CloseHandle (hImpersonationToken);
      if (hToken) CloseHandle (hToken);
   }

   return fReturn;
}

JNIEXPORT jboolean JNICALL Java_com_izforge_izpack_util_win_UserInfo_validatePassword(JNIEnv *env, jobject obj, jstring jUsername, jstring jDomain, jstring jPassword) {
	jboolean result = false;
	LPCTSTR username = NULL;
	LPCTSTR domain = NULL;
	LPCTSTR password = NULL;
	HANDLE hToken;

	if (jUsername != NULL) 
		username = (LPCTSTR)env->GET_STRING_CHARS( jUsername, 0);
	if (jDomain != NULL) 
		domain = (LPCTSTR)env->GET_STRING_CHARS( jDomain, 0);
	if (jPassword != NULL) 
		password = (LPCTSTR)env->GET_STRING_CHARS( jPassword, 0);
	__try {
		result = ::LogonUser(username, domain, password, LOGON32_LOGON_NETWORK, LOGON32_PROVIDER_DEFAULT, &hToken);
	}
	__finally {
		::CloseHandle(hToken);
		if (username != NULL)
			env->RELEASE_STRING_CHARS( jUsername, username);
		if (domain != NULL)
			env->RELEASE_STRING_CHARS( jDomain, domain);
		if (password != NULL)
			env->RELEASE_STRING_CHARS( jPassword, password);
	}
	return result;
}

/*
 * Class:     com_izforge_izpack_util_win_UserInfo
 * Method:    listManagedServiceAccounts
 * Signature: ()[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_com_izforge_izpack_util_win_UserInfo_listManagedServiceAccounts(JNIEnv *env, jobject obj) {
	HMODULE hMod = ::LoadLibrary(_T("logoncli.dll"));
	PNESA pNESA = (PNESA)::GetProcAddress(hMod, "NetEnumerateServiceAccounts");
	if (NULL != pNESA) {
		DWORD dwCount = 0;
		PZPWSTR pzAccts = NULL;
		NTSTATUS result = pNESA(NULL, NULL, &dwCount, &pzAccts);
    jclass StringObject = env->FindClass("java/lang/String");
    if (dwCount > 0) {
      jobjectArray strResult = env->NewObjectArray(dwCount, StringObject, NULL);
      for (DWORD i = 0; i < dwCount; i++) {
        env->SetObjectArrayElement(strResult, i, env->NewString((jchar*)pzAccts[i], (jsize)_tcslen(pzAccts[i])));
      }
		  ::NetApiBufferFree(pzAccts);
      return strResult;
    } else {
      jobjectArray strResult = env->NewObjectArray(3, StringObject, NULL);
      env->SetObjectArrayElement(strResult, 0, env->NewString((jchar*)L"Kalle", 5));
      env->SetObjectArrayElement(strResult, 1, env->NewString((jchar*)L"Nisse", 5));
      env->SetObjectArrayElement(strResult, 2, env->NewString((jchar*)L"Putte", 5));
      return strResult;
    }
	}
  return NULL;
}
