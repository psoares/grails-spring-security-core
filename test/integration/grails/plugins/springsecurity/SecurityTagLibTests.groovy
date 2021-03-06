/* Copyright 2006-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugins.springsecurity

import java.security.Principal

import javax.servlet.FilterChain

import grails.test.GroovyPagesTestCase

import org.codehaus.groovy.grails.plugins.springsecurity.SpringSecurityUtils
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AuthenticationDetailsSource
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.GrantedAuthorityImpl
import org.springframework.security.core.context.SecurityContextHolder as SCH
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsChecker
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.authentication.switchuser.SwitchUserFilter

/**
 * Integration tests for <code>SecurityTagLib</code>.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class SecurityTagLibTests extends GroovyPagesTestCase {

	private final _user = new Expando()

	boolean transactional = false

	def springSecurityService

	/**
	 * Test ifAllGranted().
	 */
	void testIfAllGranted() {
		String body = 'the_content'

		authenticate 'role1'
		assertOutputEquals '', "<sec:ifAllGranted roles='role1,role2'>${body}</sec:ifAllGranted>"

		authenticate 'role2,role1'
		assertOutputEquals body, "<sec:ifAllGranted roles='role1,role2'>${body}</sec:ifAllGranted>"
	}

	/**
	 * Test ifNotGranted().
	 */
	void testIfNotGrantedMissingRole() {
		String body = 'the_content'

		authenticate 'role1'
		assertOutputEquals '', "<sec:ifNotGranted roles='role1,role2'>${body}</sec:ifNotGranted>"

		authenticate 'role3'
		assertOutputEquals body, "<sec:ifNotGranted roles='role1,role2'>${body}</sec:ifNotGranted>"
	}

	/**
	 * Test ifAnyGranted().
	 */
	void testIfAnyGranted() {
		String body = 'the_content'

		authenticate 'role3'
		assertOutputEquals '', "<sec:ifAnyGranted roles='role1,role2'>${body}</sec:ifAnyGranted>"

		authenticate 'role2'
		assertOutputEquals body, "<sec:ifAnyGranted roles='role1,role2'>${body}</sec:ifAnyGranted>"
	}

	/**
	 * Test ifLoggedIn().
	 */
	void testIfLoggedInTrue() {
		String body = 'the_content'

		assertOutputEquals '', "<sec:ifLoggedIn roles='role1,role2'>${body}</sec:ifLoggedIn>"

		authenticate 'role1'
		assertOutputEquals body, "<sec:ifLoggedIn roles='role1,role2'>${body}</sec:ifLoggedIn>"
	}

	/**
	 * Test ifNotLoggedIn().
	 */
	void testIfNotLoggedIn() {
		String body = 'the_content'

		assertOutputEquals body, "<sec:ifNotLoggedIn roles='role1,role2'>${body}</sec:ifNotLoggedIn>"

		authenticate 'role1'
		assertOutputEquals '', "<sec:ifNotLoggedIn roles='role1,role2'>${body}</sec:ifNotLoggedIn>"
	}

	/**
	 * Test loggedInUserInfo() for a principal that has a 'domainClass' property.
	 */
	void testLoggedInUserInfoWithDomainClass() {
		String fullName = 'First Last'
		_user.fullName = fullName

		assertOutputEquals '', "<sec:loggedInUserInfo field='fullName'/>"

		def principal = new HasDomainClass('username', fullName, 'role1', _user)
		authenticate principal, 'role1'

		assertOutputEquals fullName, "<sec:loggedInUserInfo field='fullName'/>"
	}

	/**
	 * Test loggedInUserInfo() with a nested property.
	 */
	void testLoggedInUserInfoNested() {
		String fullName = 'First Last'
		_user.foo = [bar: [fullName: fullName]]

		assertOutputEquals '', "<sec:loggedInUserInfo field='foo.bar.fullName'/>"

		def principal = new HasDomainClass('username', 'fullName', 'role1', _user)
		authenticate principal, 'role1'

		assertOutputEquals fullName, "<sec:loggedInUserInfo field='foo.bar.fullName'/>"

		assertOutputEquals '', "<sec:loggedInUserInfo field='foo.fullName'/>"
	}

	/**
	 * Test loggedInUserInfo() for a principal that doesn't have a 'domainClass' property.
	 */
	void testLoggedInUserInfoWithoutDomainClass() {
		String fullName = 'First Last'
		_user.fullName = fullName

		assertOutputEquals '', "<sec:loggedInUserInfo field='fullName'/>"

		def principal = new NoDomainClass('username', fullName, 'role1')
		authenticate principal, 'role1'

		assertOutputEquals fullName, "<sec:loggedInUserInfo field='fullName'/>"
	}

	void testUsername() {
		assertOutputEquals '', '<sec:username/>'

		authenticate 'role1'
		assertOutputEquals 'username1', '<sec:username/>'
	}

	void testSwitched() {
		String body = 'the_content'

		assertOutputEquals body, "<sec:ifNotSwitched>${body}</sec:ifNotSwitched>"
		assertOutputEquals '', "<sec:ifSwitched>${body}</sec:ifSwitched>"

		authenticate 'role1'
		assertOutputEquals body, "<sec:ifNotSwitched>${body}</sec:ifNotSwitched>"
		assertOutputEquals '', "<sec:ifSwitched>${body}</sec:ifSwitched>"

		switchUser()

		assertOutputEquals body, "<sec:ifSwitched>${body}</sec:ifSwitched>"
		assertOutputEquals '', "<sec:ifNotSwitched>${body}</sec:ifNotSwitched>"
	}

	void testSwitchedUserOriginalUsername() {
		assertOutputEquals '', "<sec:switchedUserOriginalUsername/>"
		authenticate 'role1'
		assertOutputEquals '', "<sec:switchedUserOriginalUsername/>"

		switchUser()

		assertOutputEquals 'username1', "<sec:switchedUserOriginalUsername/>"
	}

	void testAccess() {
		String body = 'the_content'

		assertOutputEquals '', """<sec:access expression="hasRole('role1')">${body}</sec:access>"""
		assertOutputEquals body, """<sec:noAccess expression="hasRole('role1')">${body}</sec:noAccess>"""

		authenticate 'role1'
		assertOutputEquals body, """<sec:access expression="hasRole('role1')">${body}</sec:access>"""
		assertOutputEquals '', """<sec:noAccess expression="hasRole('role1')">${body}</sec:noAccess>"""
	}

	private void switchUser() {
		def filter = new SwitchUserFilter()
		def request = new MockHttpServletRequest('POST', '/j_spring_security_switch_user')
		request.addParameter 'j_username', 'somebody'
		def response = new MockHttpServletResponse()

		boolean chainCalled = false
		boolean onAuthenticationSuccessCalled = false
		def chain = [doFilter: { req, res -> chainCalled = true }] as FilterChain
		def onAuthenticationSuccess = { req, res, targetUser -> onAuthenticationSuccessCalled = true }
		filter.successHandler = [onAuthenticationSuccess: onAuthenticationSuccess] as AuthenticationSuccessHandler

		def user = new User('somebody', 'password', true, true, true, true,
				[new GrantedAuthorityImpl('ROLE_USER')])
		def loadUserByUsername = { String username -> user }
		filter.userDetailsService = [loadUserByUsername: loadUserByUsername] as UserDetailsService
		filter.userDetailsChecker = [check: { details -> }] as UserDetailsChecker
		filter.authenticationDetailsSource = [buildDetails: { req -> '' }] as AuthenticationDetailsSource

		filter.doFilter request, response, chain

		assertFalse chainCalled
		assertTrue onAuthenticationSuccessCalled
	}

	private void authenticate(String roles) {
		authenticate new SimplePrincipal(name: 'username1', domainClass: _user), roles
	}

	private void authenticate(principal, String roles) {
		Authentication authentication = new TestingAuthenticationToken(
				principal, null, SpringSecurityUtils.parseAuthoritiesString(roles))
		authentication.authenticated = true
		SCH.context.authentication = authentication
	}

	/**
	 * {@inheritDoc}
	 * @see junit.framework.TestCase#tearDown()
	 */
	@Override
	protected void tearDown() {
		super.tearDown()
		SCH.clearContext()
	}
}

class NoDomainClass extends User {

	private final String _fullName

	NoDomainClass(String username, String fullName, String roles) {
		super(username, 'password', true, true, true, true, SpringSecurityUtils.parseAuthoritiesString(roles))
		_fullName = fullName
	}

	String getFullName() { _fullName }
}

class HasDomainClass extends User {

	private final String _fullName
	private final _domainClass

	HasDomainClass(String username, String fullName, String roles, domainClass) {
		super(username, 'password', true, true, true, true, SpringSecurityUtils.parseAuthoritiesString(roles))
		_fullName = fullName
		_domainClass = domainClass
	}

	String getFullName() { _fullName }

	def getDomainClass() { _domainClass }
}

class SimplePrincipal implements Principal {
	String name
	def domainClass

	String getUsername() { name }
}
