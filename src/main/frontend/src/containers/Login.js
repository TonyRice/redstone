import React, { useState, useEffect } from "react";
import { Button, Form, FormControl, FormLabel } from "react-bootstrap";
import "./Login.css";
import DigestFetch from "digest-fetch";

export default function Login(props) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [hasError, setHasError] = useState(false);

  function validateForm() {
    return username.length > 0 && password.length > 0;
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setHasError(false);
    try {
      const client = new DigestFetch(username, password, {
        statusCode: 403
      });
      const resp = await client.fetch('/v1/login');
      if (resp.status === 200) {
        props.userHasAuthenticated(true);
        props.history.push("/");
      } else {
        setHasError(true);
      }

    } catch (e) {
      console.error(e);
      setHasError(true);
    }
  }

  return (
    <div className="Login">
      <form onSubmit={handleSubmit}>
        <Form.Group>
          <Form.Text className="text-muted">
            Please enter your login information.
          </Form.Text>
        </Form.Group>
        <Form.Group controlId="username">
          <Form.Label>Username</Form.Label>
          <Form.Control
            isInvalid={hasError}
            autoFocus
            type="text"
            value={username}
            onChange={e => setUsername(e.target.value)}
          />
        </Form.Group>
        <Form.Group controlId="password">
          <Form.Label>Password</Form.Label>
          <Form.Control isInvalid={hasError}
            value={password}
            onChange={e => setPassword(e.target.value)}
            type="password"
          />
        </Form.Group>
        <Button block disabled={!validateForm()} type="submit">
          Login
        </Button>
      </form>
    </div>
  );
}